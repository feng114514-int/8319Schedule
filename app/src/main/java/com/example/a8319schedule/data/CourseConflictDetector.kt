package com.example.a8319schedule.data

/**
 * 课程冲突检测工具类
 * 检测同一课表中，同一天、同一周次、时间段有重叠的课程
 */
object CourseConflictDetector {

    /**
     * 冲突信息
     */
    data class ConflictInfo(
        val conflictingCourse: Course,
        val overlapStart: Int,
        val overlapEnd: Int
    )

    /**
     * 检测单个课程实例与现有课程的冲突
     *
     * @param newCourse 要添加/更新的课程
     * @param existingCourses 现有课程列表
     * @param excludeCourseId 排除的课程ID（编辑时排除自身）
     * @return 冲突的课程列表
     */
    fun detectConflicts(
        newCourse: Course,
        existingCourses: List<Course>,
        excludeCourseId: Long = 0
    ): List<ConflictInfo> {
        return existingCourses
            .filter { existing ->
                // 排除自身
                existing.id != excludeCourseId &&
                // 同一课表
                existing.scheduleId == newCourse.scheduleId &&
                // 同一天
                existing.dayOfWeek == newCourse.dayOfWeek &&
                // 同一周次
                existing.weekNumber == newCourse.weekNumber &&
                // 时间段重叠：A的起始 <= B的结束 && B的起始 <= A的结束
                existing.startPeriod <= newCourse.endPeriod &&
                newCourse.startPeriod <= existing.endPeriod
            }
            .map { existing ->
                ConflictInfo(
                    conflictingCourse = existing,
                    overlapStart = maxOf(existing.startPeriod, newCourse.startPeriod),
                    overlapEnd = minOf(existing.endPeriod, newCourse.endPeriod)
                )
            }
    }

    /**
     * 检测跨多周的课程与现有课程的冲突
     *
     * @param name 课程名
     * @param dayOfWeek 星期几
     * @param startPeriod 开始节次
     * @param endPeriod 结束节次
     * @param startWeek 起始周
     * @param endWeek 结束周
     * @param scheduleId 课表ID
     * @param existingCourses 现有课程列表
     * @param excludeCourseId 排除的课程ID
     * @return 按周次分组的冲突信息
     */
    fun detectConflictsForWeekRange(
        name: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int,
        startWeek: Int,
        endWeek: Int,
        scheduleId: Long,
        existingCourses: List<Course>,
        excludeCourseId: Long = 0
    ): Map<Int, List<ConflictInfo>> {
        val conflicts = mutableMapOf<Int, List<ConflictInfo>>()
        for (week in startWeek..endWeek) {
            val templateCourse = Course(
                id = 0,
                name = name,
                dayOfWeek = dayOfWeek,
                weekNumber = week,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                scheduleId = scheduleId
            )
            val weekConflicts = detectConflicts(templateCourse, existingCourses, excludeCourseId)
            if (weekConflicts.isNotEmpty()) {
                conflicts[week] = weekConflicts
            }
        }
        return conflicts
    }

    /**
     * 格式化冲突信息为可读文本
     */
    fun formatConflicts(conflicts: Map<Int, List<ConflictInfo>>): String {
        if (conflicts.isEmpty()) return ""
        return buildString {
            append("发现 ${conflicts.values.sumOf { it.size }} 处课程冲突：\n")
            conflicts.forEach { (week, conflictList) ->
                conflictList.forEach { info ->
                    append("- 第${week}周 与「${info.conflictingCourse.name}」(第${info.overlapStart}-${info.overlapEnd}节) 冲突\n")
                }
            }
        }
    }

    /**
     * 格式化单周冲突信息为简短文本
     */
    fun formatConflictsBrief(conflicts: List<ConflictInfo>): String {
        if (conflicts.isEmpty()) return ""
        return conflicts.joinToString("、") {
            "「${it.conflictingCourse.name}」(第${it.overlapStart}-${it.overlapEnd}节)"
        }
    }
}
