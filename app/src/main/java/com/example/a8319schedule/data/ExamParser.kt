package com.example.a8319schedule.data

import java.util.Calendar

/**
 * 解析强智教务系统考试安排页面的 HTML 表格。
 *
 * 表格结构（id="dataList"）列序：
 * 0 序号 | 1 校区 | 2 考试场次 | 3 课程编号 | 4 课程名称 | 5 授课教师
 * 6 监考老师 | 7 考试时间 | 8 考场 | 9 座位号 | 10 准考证号 | 11 备注 | 12 操作
 *
 * 考试时间格式："2026-06-29 10:25~12:05"
 */
object ExamParser {

    private val tableRegex = Regex("""<table[^>]*id=["']dataList["'][^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE)
    private val trRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
    private val tdRegex = Regex("<td[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)
    private val timeRegex = Regex("(\\d{4})-(\\d{2})-(\\d{2})\\s*(\\d{2}):(\\d{2})~(\\d{2}):(\\d{2})")

    data class ParseResult(val exams: List<Exam>, val message: String)

    fun parse(html: String, scheduleId: Long, xnxqid: String): ParseResult {
        val tableMatch = tableRegex.find(html)
        if (tableMatch == null) {
            return ParseResult(emptyList(), "未找到考试数据表格")
        }

        val rows = trRegex.findAll(tableMatch.groupValues[1]).toList()
        if (rows.isEmpty()) {
            return ParseResult(emptyList(), "考试表格为空")
        }

        val exams = mutableListOf<Exam>()
        // 跳过表头行（第一行是 <th>）
        for (row in rows.drop(1)) {
            val cells = tdRegex.findAll(row.groupValues[1])
                .map { cleanCell(it.groupValues[1]) }
                .toList()

            // 至少需要 12 列（含操作列共 13 列）
            if (cells.size < 12) continue
            // 跳过空课程名
            if (cells[4].isBlank()) continue

            val examTimeRaw = cells[7]
            val (startTs, endTs) = parseTime(examTimeRaw)

            exams.add(
                Exam(
                    scheduleId = scheduleId,
                    courseName = cells[4],
                    courseCode = cells[3],
                    sessionName = cells[2],
                    campus = cells[1],
                    teacher = cells[5],
                    invigilator = cells[6],
                    examTimeRaw = examTimeRaw,
                    examStartTimestamp = startTs,
                    examEndTimestamp = endTs,
                    examRoom = cells[8],
                    seatNumber = cells[9],
                    admissionTicket = cells[10],
                    remark = cells.getOrElse(11) { "" },
                    xnxqid = xnxqid
                )
            )
        }

        return if (exams.isEmpty()) {
            ParseResult(emptyList(), "本学期暂无考试安排")
        } else {
            ParseResult(exams, "成功解析 ${exams.size} 条考试安排")
        }
    }

    private fun cleanCell(raw: String): String {
        return raw
            .replace("&nbsp;", " ")
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("<[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    fun parseTime(raw: String): Pair<Long, Long> {
        val m = timeRegex.find(raw) ?: return 0L to 0L
        return try {
            val (y, mo, d, sh, sm, eh, em) = m.destructured
            val start = Calendar.getInstance().apply {
                set(y.toInt(), mo.toInt() - 1, d.toInt(), sh.toInt(), sm.toInt(), 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = Calendar.getInstance().apply {
                set(y.toInt(), mo.toInt() - 1, d.toInt(), eh.toInt(), em.toInt(), 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            start to end
        } catch (e: Exception) {
            0L to 0L
        }
    }
}
