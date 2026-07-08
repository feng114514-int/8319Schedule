package com.example.a8319schedule.data

/**
 * 解析强智教务系统培养方案（执行计划）页面的 HTML 表格。
 *
 * 页面：/jsxsd/pyfa/pyfa_query（GET，无参数）
 * 表格结构（id="dataList"）列序：
 * 0 序号 | 1 开课学期 | 2 课程编号 | 3 课程名称 | 4 开课单位 | 5 学分
 * 6 总学时 | 7 考核方式 | 8 课程性质 | 9 课程属性 | 10 是否考试
 */
object TrainingPlanParser {

    private val tableRegex = Regex("""<table[^>]*id=["']dataList["'][^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE)
    private val trRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
    private val tdRegex = Regex("<td[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)

    data class ParseResult(val plans: List<TrainingPlan>, val message: String)

    fun parse(html: String, scheduleId: Long): ParseResult {
        val tableMatch = tableRegex.find(html)
        if (tableMatch == null) {
            return ParseResult(emptyList(), "未找到培养方案表格")
        }

        val rows = trRegex.findAll(tableMatch.groupValues[1]).toList()
        if (rows.isEmpty()) {
            return ParseResult(emptyList(), "培养方案表格为空")
        }

        val plans = mutableListOf<TrainingPlan>()
        // 跳过表头行（第一行是 <th>）
        for (row in rows.drop(1)) {
            val cells = tdRegex.findAll(row.groupValues[1])
                .map { cleanCell(it.groupValues[1]) }
                .toList()

            // 至少需要 11 列
            if (cells.size < 11) continue
            // 跳过空课程名
            if (cells[3].isBlank()) continue

            plans.add(
                TrainingPlan(
                    scheduleId = scheduleId,
                    seq = cells[0].toIntOrNull() ?: plans.size + 1,
                    semester = cells[1],
                    courseCode = cells[2],
                    courseName = cells[3],
                    department = cells[4],
                    credit = cells[5],
                    totalHours = cells[6],
                    assessmentType = cells[7],
                    courseNature = cells[8],
                    courseAttribute = cells[9],
                    isExam = cells[10]
                )
            )
        }

        return if (plans.isEmpty()) {
            ParseResult(emptyList(), "暂无培养方案数据")
        } else {
            ParseResult(plans, "成功解析 ${plans.size} 条培养方案")
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
}
