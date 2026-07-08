package com.example.a8319schedule.data

/**
 * 解析强智教务系统成绩查询页面的 HTML 表格。
 *
 * 页面：/jsxsd/kscj/cjcx_list（POST，查询全部时空参数）
 * 表格结构（id="dataList"）列序：
 * 0 序号 | 1 开课学期 | 2 课程编号 | 3 课程名称 | 4 分组名 | 5 成绩
 * 6 成绩标识 | 7 学分 | 8 总学时 | 9 绩点 | 10 补重学期 | 11 考核方式
 * 12 考试性质 | 13 课程属性 | 14 课程性质 | 15 课程类别
 *
 * 注意：成绩单元格结构异常，末尾有孤立 </td></td>，但非贪婪正则匹配第一个 </td>
 * 即可正确闭合，孤立 </td> 不产生匹配被忽略，后续单元格不受影响。
 */
object ScoreParser {

    private val tableRegex = Regex("""<table[^>]*id=["']dataList["'][^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE)
    private val trRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
    private val tdRegex = Regex("<td[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)

    data class ParseResult(val scores: List<Score>, val message: String)

    fun parse(html: String, scheduleId: Long): ParseResult {
        val tableMatch = tableRegex.find(html)
        if (tableMatch == null) {
            return ParseResult(emptyList(), "未找到成绩表格")
        }

        val rows = trRegex.findAll(tableMatch.groupValues[1]).toList()
        if (rows.isEmpty()) {
            return ParseResult(emptyList(), "成绩表格为空")
        }

        val scores = mutableListOf<Score>()
        // 跳过表头行（第一行是 <th>）
        for (row in rows.drop(1)) {
            val cells = tdRegex.findAll(row.groupValues[1])
                .map { cleanCell(it.groupValues[1]) }
                .toList()

            // 至少需要 10 列（课程属性、课程性质、课程类别可能缺失，用空字符串填充）
            if (cells.size < 10) continue
            // 跳过空课程名
            if (cells.getOrNull(3).isNullOrBlank()) continue

            scores.add(
                Score(
                    scheduleId = scheduleId,
                    seq = cells.getOrNull(0)?.toIntOrNull() ?: scores.size + 1,
                    semester = cells.getOrNull(1) ?: "",
                    courseCode = cells.getOrNull(2) ?: "",
                    courseName = cells.getOrNull(3) ?: "",
                    score = cells.getOrNull(5) ?: "",
                    scoreFlag = cells.getOrNull(6) ?: "",
                    credit = cells.getOrNull(7) ?: "",
                    totalHours = cells.getOrNull(8) ?: "",
                    gradePoint = cells.getOrNull(9) ?: "",
                    makeupSemester = cells.getOrNull(10) ?: "",
                    assessmentType = cells.getOrNull(11) ?: "",
                    examNature = cells.getOrNull(12) ?: "",
                    courseAttribute = cells.getOrNull(13) ?: "",
                    courseNature = cells.getOrNull(14) ?: "",
                    courseCategory = cells.getOrNull(15) ?: ""
                )
            )
        }

        return if (scores.isEmpty()) {
            ParseResult(emptyList(), "暂无成绩数据")
        } else {
            ParseResult(scores, "成功解析 ${scores.size} 条成绩")
        }
    }

    private fun cleanCell(raw: String): String {
        return raw
            .replace("<!--[\\s\\S]*?-->".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("&nbsp;", " ")
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("<[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
