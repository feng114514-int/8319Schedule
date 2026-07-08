package com.example.a8319schedule.data

/**
 * 解析强智教务系统培养方案及完成情况页面（topyfamx）中的"课程设置总表"。
 *
 * 页面：/jsxsd/pyfa/topyfamx（GET，无参数）
 * 目标表格 id="mxh"，共 14 列：
 * 0 课程体系 | 1 选课组 | 2 课程编号 | 3 课程名称 | 4 完成情况
 * 5 课程性质 | 6 课程属性 | 7 学分 | 8 讲课学时 | 9 实践学时
 * 10 实验学时 | 11 线上学时 | 12 总学时 | 13 开设学期
 *
 * 课程体系列仅在分组首行出现（带 rowspan=N），后续行缺该列（13 列），
 * 解析时沿用上一行的课程体系值。每个分组后有一个 colspan=7 的"小计"行，
 * 表格末尾还有一个"合计"行，均需跳过。
 */
object TrainingPlanAllParser {

    private val tableRegex =
        Regex("""<table[^>]*\sid\s*=\s*['"]mxh['"][^>]*>([\s\S]*?)</table>""", RegexOption.IGNORE_CASE)
    private val trRegex = Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE)
    private val tdRegex = Regex("<td[^>]*>([\\s\\S]*?)</td>", RegexOption.IGNORE_CASE)

    data class ParseResult(val plans: List<TrainingPlanAll>, val message: String)

    fun parse(html: String, scheduleId: Long): ParseResult {
        val tableMatch = tableRegex.find(html)
        if (tableMatch == null) {
            return ParseResult(emptyList(), "未找到课程设置总表")
        }

        val rows = trRegex.findAll(tableMatch.groupValues[1]).toList()
        if (rows.isEmpty()) {
            return ParseResult(emptyList(), "课程设置总表为空")
        }

        val plans = mutableListOf<TrainingPlanAll>()
        var currentSystem = ""

        for (row in rows) {
            val cells = tdRegex.findAll(row.groupValues[1])
                .map { cleanCell(it.groupValues[1]) }
                .toList()

            // 表头行无 TD（含 TH），或单元格过少，跳过
            if (cells.isEmpty()) continue

            val first = cells.first().trim()
            // 跳过小计 / 合计行
            if (first == "小计" || first == "合计") continue

            when (cells.size) {
                14 -> {
                    // 分组首行：第 0 列为课程体系
                    currentSystem = cells[0].ifBlank { currentSystem }
                    addPlan(plans, scheduleId, currentSystem, cells, offset = 1)
                }
                13 -> {
                    // 普通行：沿用课程体系，从第 0 列（选课组）开始
                    if (currentSystem.isEmpty()) continue
                    addPlan(plans, scheduleId, currentSystem, cells, offset = 0)
                }
                else -> continue // 异常行，跳过
            }
        }

        return if (plans.isEmpty()) {
            ParseResult(emptyList(), "暂无课程设置数据")
        } else {
            ParseResult(plans, "成功解析 ${plans.size} 条课程设置")
        }
    }

    /**
     * 按 13 列数据（选课组 → 开设学期）构造实体。
     * [cells] 从 [offset] 起依次为：选课组、课程编号、课程名称、完成情况、
     * 课程性质、课程属性、学分、讲课学时、实践学时、实验学时、线上学时、总学时、开设学期。
     */
    private fun addPlan(
        plans: MutableList<TrainingPlanAll>,
        scheduleId: Long,
        courseSystem: String,
        cells: List<String>,
        offset: Int
    ) {
        val courseName = cells.getOrNull(offset + 2)?.trim() ?: ""
        if (courseName.isEmpty()) return

        plans.add(
            TrainingPlanAll(
                scheduleId = scheduleId,
                courseSystem = courseSystem,
                courseGroup = cells.getOrNull(offset + 0) ?: "",
                courseCode = cells.getOrNull(offset + 1) ?: "",
                courseName = courseName,
                completionStatus = cells.getOrNull(offset + 3) ?: "",
                courseNature = cells.getOrNull(offset + 4) ?: "",
                courseAttribute = cells.getOrNull(offset + 5) ?: "",
                credit = cells.getOrNull(offset + 6) ?: "",
                lectureHours = cells.getOrNull(offset + 7) ?: "",
                practiceHours = cells.getOrNull(offset + 8) ?: "",
                experimentHours = cells.getOrNull(offset + 9) ?: "",
                onlineHours = cells.getOrNull(offset + 10) ?: "",
                totalHours = cells.getOrNull(offset + 11) ?: "",
                openSemester = cells.getOrNull(offset + 12) ?: ""
            )
        )
    }

    private fun cleanCell(raw: String): String {
        return raw
            // 先移除 HTML 注释（总学时列含 <!-- ... -->）
            .replace("<!--[\\s\\S]*?-->".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), " ")
            .replace("<[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
