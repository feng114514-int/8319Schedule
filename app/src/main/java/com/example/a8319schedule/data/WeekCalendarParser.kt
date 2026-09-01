package com.example.a8319schedule.data

import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 教学周历解析器：从教务系统"教学周历"页面（/jsxsd/jxzl/jxzl_query）的 #kbtable 表格中
 * 识别第 1 周周一的日期，作为学期开学日期。
 *
 * 周历页与课表页的表格 ID 同为 kbtable，但结构不同：周历每行一个教学周，
 * 第一列为周次，其后各列依次为周一至周日。
 *
 * 日期显示规则（解析难点）：
 * - 并非每个格子都带月份：通常只有部分格子（如周末）显示"9月5日"这类完整月日，
 *   其余仅显示"31"这类"日"，单独的"日"无法定位月份，不能直接使用；
 * - 跨月周（如周一 8/31、周六 9/5）：必须用带月份的格子按列位置（周几）回推到周一，
 *   绝不能把周末格子的月份直接套用到周一上。
 *
 * 解析策略：
 * 1. 优先读单元格 title 属性中的完整日期（如 title='2026年08月31'）；
 * 2. 其次读可见文本中的完整日期或"月日"（年份取与今天最接近的候选，处理跨年导入）；
 * 3. 每个可解析格子按其列位置回推出一个"周一候选"，多格子投票取共识，
 *    单个格子的格式误匹配不会导致整体识别错误；
 * 4. 若周历从第 N 周开始显示，则将周一再前移 N-1 周。
 */
object WeekCalendarParser {

    data class ParseResult(
        /** 第 1 周周一的日期；识别失败为 null */
        val firstDay: LocalDate?,
        val message: String
    )

    private val tableRegex = Regex(
        """<table[^>]*\bid\s*=\s*["']kbtable["'][^>]*>([\s\S]*?)</table>""",
        RegexOption.IGNORE_CASE
    )
    private val trRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)

    /** 保留开标签属性（title 在开标签内）与单元格内容两组捕获 */
    private val tdRegex = Regex("<td([^>]*)>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("""title\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

    /** 完整日期：2026年08月31 / 2026年8月31日 / 2026-08-31 / 2026.8.31 */
    private val fullDateRegex = Regex("""(\d{4})\s*[年\-/.]\s*(\d{1,2})\s*[月\-/.]\s*(\d{1,2})\s*日?""")

    /**
     * 月日（不含年）：9月5日 / 09-05 / 9.5。前后不得紧邻数字，且末尾不得紧跟"周/节"字
     * （排除"1-16周""第1-2节"这类课表数字片段；"9月10日 教师节"中"节"不紧邻数字，不受影响）。
     */
    private val monthDayRegex = Regex("""(?<!\d)(\d{1,2})\s*[月\-/.]\s*(\d{1,2})\s*日?(?!\d)(?!\s*[周节])""")

    fun parse(html: String?): ParseResult {
        if (html.isNullOrBlank()) return ParseResult(null, "教学周历内容为空")
        val tableHtml = tableRegex.find(html)?.groupValues?.get(1)
            ?: return ParseResult(null, "未找到教学周历表格")
        val today = LocalDate.now()

        for (row in trRegex.findAll(tableHtml)) {
            val cells = tdRegex.findAll(row.groupValues[1]).toList()
            if (cells.size < 3) continue

            // 第一列为周次（"1" / "第1周"），表头行（"周次"）在此被过滤
            val weekNo = extractWeekNo(clean(cells[0].groupValues[2])) ?: continue

            // 快速路径：第一个日期格（周一）的 title 通常直接带完整日期（如 title='2026年08月31'），
            // 即该周周一本身，直接采用——无需回推与投票，也不受其他格子脏数据干扰
            val mondayCell = cells.getOrNull(1) ?: continue
            val directMonday = parseCellDate(
                titleRegex.find(mondayCell.groupValues[1])?.groupValues?.get(1).orEmpty(),
                today
            ) ?: parseShortTextDate(clean(mondayCell.groupValues[2]), today)

            // 通用路径降级：周一格无日期（如仅周末显示月日）时，整行投票按列位置回推周一
            val monday = directMonday ?: resolveMonday(cells.drop(1), today) ?: continue

            // 第 1 周周一即开学日期；若周历从第 N 周开始显示则前移 N-1 周
            val firstDay = monday.minusDays(((weekNo - 1) * 7).toLong())
            return ParseResult(
                firstDay,
                "已识别开学日期：${firstDay.year}年${firstDay.monthValue}月${firstDay.dayOfMonth}日"
            )
        }
        return ParseResult(null, "未在周历中识别到周次与日期")
    }

    /**
     * 从一行日期格子推算该周周一：每个可解析出日期的格子按列位置（0=周一…6=周日）
     * 回推出一个"周一候选"，投票取共识；票数并列且分散说明列结构异常，宁可放弃不猜。
     */
    private fun resolveMonday(dateCells: List<MatchResult>, today: LocalDate): LocalDate? {
        val votes = mutableMapOf<LocalDate, Int>()
        for ((index, cell) in dateCells.withIndex().take(7)) {
            val title = titleRegex.find(cell.groupValues[1])?.groupValues?.get(1).orEmpty()
            val text = clean(cell.groupValues[2])
            val date = parseCellDate(title, today)
                ?: parseShortTextDate(text, today)
                ?: continue
            votes.merge(date.minusDays(index.toLong()), 1, Int::plus)
        }
        if (votes.isEmpty()) return null
        val best = votes.maxByOrNull { it.value } ?: return null
        if (best.value == 1 && votes.size > 1) return null
        return best.key
    }

    /**
     * 解析可见文本中的日期：仅信任短小的日期串（"31"/"9月5日"/"09-05"/"2026-08-31"），
     * 长文本（课表课程信息等）直接放弃，双重防止"1-16周"这类数字片段被误当日期。
     */
    private fun parseShortTextDate(text: String, today: LocalDate): LocalDate? {
        if (text.length > 12) return null
        return parseCellDate(text, today)
    }

    /**
     * 解析单元格日期：优先含年份的完整日期；其次"月日"（年份在 [今年-1, 今年+1] 中
     * 取与今天最近者，覆盖 12 月提前导入下学期等跨年场景）。
     * 仅显示"日"（如"31"）的格子返回 null——月份未知，不参与投票。
     */
    private fun parseCellDate(source: String, today: LocalDate): LocalDate? {
        if (source.isBlank()) return null
        fullDateRegex.find(source)?.let { m ->
            val (y, mo, d) = m.destructured
            return dateOf(y.toInt(), mo.toInt(), d.toInt())
        }
        monthDayRegex.find(source)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return null
            val day = m.groupValues[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            return listOf(today.year - 1, today.year, today.year + 1)
                .mapNotNull { dateOf(it, month, day) }
                .minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(it, today)) }
        }
        return null
    }

    /** 提取周次数字："1"、"第1周"、"01" → 1；无数字（表头"周次"）→ null */
    private fun extractWeekNo(text: String): Int? =
        Regex("\\d+").find(text)?.value?.toIntOrNull()

    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (e: DateTimeException) {
        null
    }

    /** 去除标签、HTML 实体与多余空白，保留纯文本 */
    private fun clean(raw: String): String = raw
        .replace("&nbsp;", " ")
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]*>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
