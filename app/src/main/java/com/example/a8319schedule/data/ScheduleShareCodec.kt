package com.example.a8319schedule.data

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.CRC32

/**
 * 课表分享码编解码器
 *
 * 分享码格式：SCD:<版本号>:<CRC32校验和>:<编码数据>
 * v1: URL-Safe Base64(GZIP压缩的JSON)，周次为列表，颜色为Long
 * v2: Base85(GZIP压缩的JSON)，周次为范围字符串，颜色为调色板索引
 * v3: 魔改Base85(GZIP压缩的JSON)，使用自定义字符表，其余同v2
 */
object ScheduleShareCodec {

    private const val PREFIX = "SCD:"
    private const val CURRENT_VERSION = 3

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    // 调色板 —— 统一使用 CourseColors.PALETTE
    val COLOR_PALETTE = CourseColors.PALETTE.toLongArray()

    // ===== v2 紧凑序列化数据类 =====

    @Serializable
    data class ShareableSchedule(
        val n: String,           // 课表名称 (name)
        val d: String = "",      // 课表描述 (description)
        val sd: Long = 0L,       // 开学日期时间戳 (startDate)
        val tr: Int = 0,         // 总课程记录数 (totalRecords)，用于校验
        val c: List<ShareableCourse>  // 课程列表
    )

    @Serializable
    data class ShareableCourse(
        val n: String,           // 课程名称
        val t: String = "",      // 教师
        val r: String = "",      // 教室
        val w: Int,              // 星期几 (dayOfWeek)
        val wk: String,          // 周次范围，如 "1-16" 或 "1-8,10-16"
        val s: Int,              // 开始节次
        val e: Int,              // 结束节次
        val ci: Int = 0          // 调色板索引
    )

    // ===== v1 兼容数据类（仅用于解码旧版分享码）=====

    @Serializable
    data class ShareableCourseV1(
        val n: String,
        val t: String = "",
        val r: String = "",
        val w: Int,
        val ws: List<Int>,
        val s: Int,
        val e: Int,
        val cl: Long = 0xFF4CAF50L
    )

    @Serializable
    data class ShareableScheduleV1(
        val n: String,
        val d: String = "",
        val sd: Long = 0L,
        val c: List<ShareableCourseV1>
    )

    // ===== 周次范围 ↔ 列表 转换 =====

    /**
     * 将周次列表转为紧凑范围字符串
     * [1,2,3,4,5,6,7,8] → "1-8"
     * [1,2,3,5,6,7,9,10] → "1-3,5-7,9-10"
     * [5] → "5"
     */
    fun weeksToRange(weeks: List<Int>): String {
        if (weeks.isEmpty()) return ""
        val sorted = weeks.sorted()
        val ranges = mutableListOf<String>()
        var rangeStart = sorted[0]
        var rangeEnd = sorted[0]

        for (i in 1 until sorted.size) {
            if (sorted[i] == rangeEnd + 1) {
                rangeEnd = sorted[i]
            } else {
                ranges.add(formatRange(rangeStart, rangeEnd))
                rangeStart = sorted[i]
                rangeEnd = sorted[i]
            }
        }
        ranges.add(formatRange(rangeStart, rangeEnd))
        return ranges.joinToString(",")
    }

    private fun formatRange(start: Int, end: Int): String {
        return if (start == end) "$start" else "$start-$end"
    }

    /**
     * 将范围字符串解析为周次列表
     * "1-8" → [1,2,3,4,5,6,7,8]
     * "1-3,5-7,9" → [1,2,3,5,6,7,9]
     */
    fun rangeToWeeks(range: String): List<Int> {
        if (range.isBlank()) return emptyList()
        val weeks = mutableListOf<Int>()
        range.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (startStr, endStr) = trimmed.split("-", limit = 2)
                val start = startStr.trim().toIntOrNull() ?: return@forEach
                val end = endStr.trim().toIntOrNull() ?: return@forEach
                for (w in start..end) {
                    weeks.add(w)
                }
            } else {
                trimmed.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        return weeks.sorted()
    }

    // ===== 调色板索引 ↔ 颜色值 =====

    /**
     * 将颜色值映射到调色板索引（找最近的颜色）
     */
    fun colorToPaletteIndex(color: Long): Int {
        // 精确匹配
        val exactIndex = COLOR_PALETTE.indexOf(color)
        if (exactIndex >= 0) return exactIndex

        // 找最接近的颜色（欧氏距离）
        val targetR = ((color shr 16) and 0xFF).toInt()
        val targetG = ((color shr 8) and 0xFF).toInt()
        val targetB = (color and 0xFF).toInt()

        var bestIndex = 0
        var bestDist = Int.MAX_VALUE
        for (i in COLOR_PALETTE.indices) {
            val c = COLOR_PALETTE[i]
            val r = ((c shr 16) and 0xFF).toInt()
            val g = ((c shr 8) and 0xFF).toInt()
            val b = (c and 0xFF).toInt()
            val dist = (r - targetR) * (r - targetR) +
                       (g - targetG) * (g - targetG) +
                       (b - targetB) * (b - targetB)
            if (dist < bestDist) {
                bestDist = dist
                bestIndex = i
            }
        }
        return bestIndex
    }

    fun paletteIndexToColor(index: Int): Long {
        return if (index in COLOR_PALETTE.indices) COLOR_PALETTE[index] else COLOR_PALETTE[0]
    }

    // ===== 编码：课表数据 → 分享码字符串 =====

    fun encode(scheduleInfo: ScheduleInfo, courses: List<Course>): String {
        // 按 courseGroupId 分组合并周次
        val groupedCourses = courses.groupBy {
            // 同一门课在不同天/节次应保留为独立条目，只合并同一时间的不同周次
            "${it.courseGroupId.ifBlank { "${it.name}_${it.teacher}_${it.classroom}" }}_${it.dayOfWeek}_${it.startPeriod}_${it.endPeriod}"
        }

        val shareableCourses = groupedCourses.map { (_, group) ->
            val first = group.first()
            val weeks = group.map { it.weekNumber }.sorted()
            ShareableCourse(
                n = first.name,
                t = first.teacher,
                r = first.classroom,
                w = first.dayOfWeek,
                wk = weeksToRange(weeks),
                s = first.startPeriod,
                e = first.endPeriod,
                ci = colorToPaletteIndex(first.color)
            )
        }

        val shareable = ShareableSchedule(
            n = scheduleInfo.name,
            d = scheduleInfo.description,
            sd = scheduleInfo.startDate,
            tr = courses.size,
            c = shareableCourses
        )

        val jsonString = json.encodeToString(shareable)

        // GZIP 压缩
        val compressed = gzipCompress(jsonString.toByteArray(Charsets.UTF_8))

        // Base85 编码（v3 使用魔改字符表）
        val encoded = base85Encode(compressed, useV3 = true)

        // CRC32 校验（对压缩数据）
        val crc = CRC32()
        crc.update(compressed)
        val checksum = crc.value.toString(16).uppercase().padStart(8, '0')

        val shareCodeBody = "$PREFIX$CURRENT_VERSION:$checksum:$encoded"
        return "有同学在江理课程表App向你分享了课表，复制到App内即可导入：\n$shareCodeBody"
    }

    // ===== 解码：分享码字符串 → 课表数据 =====

    sealed class DecodeResult {
        data class Success(
            val scheduleInfo: ShareableSchedule,
            val courses: List<Course>
        ) : DecodeResult()

        data class Error(val message: String) : DecodeResult()
    }

    fun decode(shareCode: String): DecodeResult {
        // 清理输入：去除首尾空白、换行符、空格
        val cleaned = shareCode.trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace(" ", "")

        // 找到 SCD: 前缀的位置（前面可能有中文说明文字）
        val prefixIndex = cleaned.indexOf(PREFIX)
        if (prefixIndex == -1) {
            return DecodeResult.Error("不是有效的分享码（缺少 SCD: 前缀）")
        }

        // 截取 SCD: 之后的部分
        val shareCodeBody = cleaned.substring(prefixIndex + PREFIX.length)

        val parts = shareCodeBody.split(":", limit = 3)
        if (parts.size != 3) {
            return DecodeResult.Error("分享码格式错误")
        }

        val version = parts[0].toIntOrNull() ?: return DecodeResult.Error("版本号无效")
        if (version > CURRENT_VERSION) {
            return DecodeResult.Error("分享码版本(v${version})高于当前app支持版本(v${CURRENT_VERSION})，请更新app后重试")
        }

        val expectedChecksum = parts[1]
        val encodedData = parts[2]

        // 解码
        val compressed = when (version) {
            1 -> {
                // v1 使用 URL-Safe Base64
                try {
                    Base64.decode(encodedData, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                } catch (e: Exception) {
                    return DecodeResult.Error("分享码数据损坏，无法解码")
                }
            }
            2 -> {
                // v2 使用标准 Base85
                try {
                    base85Decode(encodedData, useV3 = false)
                } catch (e: Exception) {
                    return DecodeResult.Error("分享码数据损坏，无法解码")
                }
            }
            3 -> {
                // v3 使用魔改 Base85
                try {
                    base85Decode(encodedData, useV3 = true)
                } catch (e: Exception) {
                    return DecodeResult.Error("分享码数据损坏，无法解码")
                }
            }
            else -> return DecodeResult.Error("不支持的版本号：v${version}")
        }

        // CRC32 校验
        val crc = CRC32()
        crc.update(compressed)
        val actualChecksum = crc.value.toString(16).uppercase().padStart(8, '0')
        if (actualChecksum != expectedChecksum) {
            return DecodeResult.Error("分享码校验失败，数据可能已被截断或篡改，请重新获取")
        }

        // GZIP 解压
        val jsonBytes = try {
            gzipDecompress(compressed)
        } catch (e: Exception) {
            return DecodeResult.Error("分享码解压失败：${e.message}")
        }

        // 根据版本解析 JSON
        val jsonString = jsonBytes.toString(Charsets.UTF_8)

        when (version) {
            1 -> {
                val shareable = try {
                    json.decodeFromString<ShareableScheduleV1>(jsonString)
                } catch (e: Exception) {
                    return DecodeResult.Error("分享码数据解析失败：${e.message}")
                }
                val timestamp = System.currentTimeMillis()
                val courses = shareable.c.flatMap { sc ->
                    sc.ws.map { week ->
                        Course(
                            name = sc.n,
                            teacher = sc.t,
                            classroom = sc.r,
                            dayOfWeek = sc.w,
                            weekNumber = week,
                            startPeriod = sc.s,
                            endPeriod = sc.e,
                            color = sc.cl,
                            courseGroupId = "${sc.n}_${sc.t}_${sc.r}_${sc.s}",
                            courseInstanceId = "${sc.n}_${sc.t}_${sc.r}_${sc.s}_${week}",
                            scheduleId = 0
                        )
                    }
                }
                // 转换为 v2 的 ShareableSchedule 以便 UI 使用
                val v2Schedule = ShareableSchedule(
                    n = shareable.n,
                    d = shareable.d,
                    sd = shareable.sd,
                    c = shareable.c.map { ShareableCourse(n = it.n, t = it.t, r = it.r, w = it.w, wk = weeksToRange(it.ws), s = it.s, e = it.e, ci = colorToPaletteIndex(it.cl)) }
                )
                return DecodeResult.Success(scheduleInfo = v2Schedule, courses = courses)
            }
            2, 3 -> {
                val shareable = try {
                    json.decodeFromString<ShareableSchedule>(jsonString)
                } catch (e: Exception) {
                    return DecodeResult.Error("分享码数据解析失败：${e.message}")
                }
                val courses = shareable.c.flatMap { sc ->
                    rangeToWeeks(sc.wk).map { week ->
                        Course(
                            name = sc.n,
                            teacher = sc.t,
                            classroom = sc.r,
                            dayOfWeek = sc.w,
                            weekNumber = week,
                            startPeriod = sc.s,
                            endPeriod = sc.e,
                            color = paletteIndexToColor(sc.ci),
                            courseGroupId = "${sc.n}_${sc.t}_${sc.r}_${sc.s}",
                            courseInstanceId = "${sc.n}_${sc.t}_${sc.r}_${sc.s}_${week}",
                            scheduleId = 0
                        )
                    }
                }
                // 校验课程总数
                if (shareable.tr > 0 && courses.size != shareable.tr) {
                    return DecodeResult.Error("课程数量校验失败：预期 ${shareable.tr} 条，实际解析 ${courses.size} 条，数据可能已损坏")
                }
                return DecodeResult.Success(scheduleInfo = shareable, courses = courses)
            }
            else -> return DecodeResult.Error("不支持的版本号：v$version")
        }
    }

    // ===== GZIP 工具方法 =====

    private fun gzipCompress(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private const val MAX_DECOMPRESSED_SIZE = 1 * 1024 * 1024 // 1MB 上限，防止压缩炸弹

    private fun gzipDecompress(data: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(data)
        val bos = ByteArrayOutputStream()
        GZIPInputStream(bis).use { input ->
            val buffer = ByteArray(4096)
            var len: Int
            var totalBytes = 0L
            while (input.read(buffer).also { len = it } != -1) {
                totalBytes += len
                if (totalBytes > MAX_DECOMPRESSED_SIZE) {
                    throw IllegalArgumentException("解压数据超过大小限制(${MAX_DECOMPRESSED_SIZE / 1024}KB)")
                }
                bos.write(buffer, 0, len)
            }
        }
        return bos.toByteArray()
    }

    // ===== Base85 编解码 (RFC 1924 variant, ASCII85 subset) =====
    // 每 4 字节 → 5 字符，膨胀率 25%（Base64 为 33%）

    // v2: 标准 RFC 1924 字符表（兼容旧版分享码）
    private const val BASE85_CHARS_V2 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz!#$%&()*+-;<=>?@^_`{|}~"

    // v3: 魔改字符表，打乱顺序以防标准工具直接解码
    private const val BASE85_CHARS_V3 = "k9F!3Hx5Qz&eD0mR~p^{2}A8|V*gS1bU_f(Nc)Z`j#7wIoP\$X%Tq;r<B6GsYLM=4C>+lK?JiE@taWv-Odhnuy"

    private val BASE85_DECODE_MAP_V2 = mutableMapOf<Char, Int>()
    private val BASE85_DECODE_MAP_V3 = mutableMapOf<Char, Int>()

    init {
        BASE85_CHARS_V2.forEachIndexed { index, c -> BASE85_DECODE_MAP_V2[c] = index }
        BASE85_CHARS_V3.forEachIndexed { index, c -> BASE85_DECODE_MAP_V3[c] = index }
    }

    private fun base85Encode(data: ByteArray, useV3: Boolean = true): String {
        val chars = if (useV3) BASE85_CHARS_V3 else BASE85_CHARS_V2
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            // 每次取最多4字节，组成一个32位整数
            var value = 0L
            var nBytes = 0
            for (j in 0 until 4) {
                if (i + j < data.size) {
                    value = (value shl 8) or (data[i + j].toLong() and 0xFF)
                    nBytes++
                } else {
                    value = value shl 8
                }
            }
            i += 4

            // 编码为5个字符
            val charCount = nBytes + 1
            val encoded = CharArray(5)
            for (j in 4 downTo 0) {
                encoded[j] = chars[(value % 85).toInt()]
                value /= 85
            }

            for (j in 0 until charCount) {
                sb.append(encoded[j])
            }
        }
        return sb.toString()
    }

    private fun base85Decode(str: String, useV3: Boolean): ByteArray {
        val decodeMap = if (useV3) BASE85_DECODE_MAP_V3 else BASE85_DECODE_MAP_V2
        val result = ByteArrayOutputStream()
        var i = 0
        while (i < str.length) {
            // 确定本组字符数（最后一组可能少于5个）
            val remaining = str.length - i
            val charCount = minOf(5, remaining)
            val nBytes = charCount - 1

            // 解码5个字符为32位整数
            // 最后一组不足5字符时，按 Ascii85 标准用最大值(84)填充
            var value = 0L
            for (j in 0 until 5) {
                value *= 85
                if (j < charCount) {
                    val c = str[i + j]
                    val digit = decodeMap[c]
                        ?: throw IllegalArgumentException("Invalid Base85 character: $c")
                    value += digit.toLong()
                } else {
                    value += 84
                }
            }

            // 提取字节
            val bytes = ByteArray(4)
            for (j in 3 downTo 0) {
                bytes[j] = (value and 0xFF).toByte()
                value = value shr 8
            }

            result.write(bytes, 0, nBytes)
            i += charCount
        }
        return result.toByteArray()
    }
}
