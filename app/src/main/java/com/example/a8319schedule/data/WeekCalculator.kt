package com.example.a8319schedule.data

import java.util.Calendar

/**
 * 统一的周次计算工具类
 * 整合了 CourseViewModel、ScheduleSettingsManager、AIChatViewModel、ScheduleWidgetProvider 中重复的周次计算逻辑
 */
object WeekCalculator {

    /**
     * 根据开学日期计算当前是第几周
     * @param startDateMillis 学期开始日期（毫秒时间戳）
     * @param endDateMillis 学期结束日期（毫秒时间戳），默认为开学日期+20周
     * @param maxWeeks 最大周数，默认20
     * @return 当前周次（1 ~ maxWeeks）
     */
    fun calculateCurrentWeek(
        startDateMillis: Long,
        endDateMillis: Long = 0L,
        maxWeeks: Int = 20
    ): Int {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startCal = Calendar.getInstance().apply {
            timeInMillis = startDateMillis
            // 调整到周一
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val today = todayCal.timeInMillis
        val start = startCal.timeInMillis

        if (today < start) return 1

        if (endDateMillis > 0) {
            val end = Calendar.getInstance().apply {
                timeInMillis = endDateMillis
            }.timeInMillis
            if (today > end) {
                val totalDiff = end - start
                val totalWeeks = (totalDiff / (7 * 24 * 60 * 60 * 1000)).toInt() + 1
                return totalWeeks.coerceIn(1, maxWeeks)
            }
        }

        val diff = today - start
        val week = (diff / (7 * 24 * 60 * 60 * 1000)).toInt() + 1
        return week.coerceIn(1, maxWeeks)
    }

    /**
     * 获取默认的学期开始日期（9月1日，调整为周一）
     * 如果当前月份在9月之前，使用上一年的9月1日
     */
    fun getDefaultStartDate(): Long {
        val todayCal = Calendar.getInstance()
        val currentYear = todayCal.get(Calendar.YEAR)
        val currentMonth = todayCal.get(Calendar.MONTH)

        return Calendar.getInstance().apply {
            set(Calendar.YEAR, if (currentMonth < Calendar.SEPTEMBER) currentYear - 1 else currentYear)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * 根据 ScheduleInfo 计算当前周次
     */
    fun calculateCurrentWeekForSchedule(schedule: ScheduleInfo?): Int {
        val startDate = if (schedule != null && schedule.startDate > 0) {
            schedule.startDate
        } else {
            getDefaultStartDate()
        }
        return calculateCurrentWeek(startDate)
    }

    /**
     * 计算指定日期所在周次
     * @param targetDateMillis 目标日期（毫秒时间戳）
     * @param startDateMillis 学期开始日期（毫秒时间戳）
     * @param endDateMillis 学期结束日期，0 表示不限
     * @param maxWeeks 最大周数
     */
    fun calculateWeekForDate(
        targetDateMillis: Long,
        startDateMillis: Long,
        endDateMillis: Long = 0L,
        maxWeeks: Int = 20
    ): Int {
        val targetCal = Calendar.getInstance().apply {
            timeInMillis = targetDateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val startCal = Calendar.getInstance().apply {
            timeInMillis = startDateMillis
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val target = targetCal.timeInMillis
        val start = startCal.timeInMillis

        if (target < start) return 1

        if (endDateMillis > 0) {
            val end = Calendar.getInstance().apply {
                timeInMillis = endDateMillis
            }.timeInMillis
            if (target > end) {
                val totalDiff = end - start
                val totalWeeks = (totalDiff / (7 * 24 * 60 * 60 * 1000)).toInt() + 1
                return totalWeeks.coerceIn(1, maxWeeks)
            }
        }

        val diff = target - start
        val week = (diff / (7 * 24 * 60 * 60 * 1000)).toInt() + 1
        return week.coerceIn(1, maxWeeks)
    }

    /**
     * 计算指定日期在指定课表中的周次
     */
    fun calculateWeekForDateForSchedule(targetDateMillis: Long, schedule: ScheduleInfo?): Int {
        val startDate = if (schedule != null && schedule.startDate > 0) {
            schedule.startDate
        } else {
            getDefaultStartDate()
        }
        return calculateWeekForDate(targetDateMillis, startDate)
    }

    /**
     * 根据 SemesterSettings 计算当前周次
     */
    fun calculateCurrentWeekForSettings(settings: SemesterSettings): Int {
        return calculateCurrentWeek(settings.startDate, settings.endDate)
    }
}
