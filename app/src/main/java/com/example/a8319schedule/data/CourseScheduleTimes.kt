package com.example.a8319schedule.data

/**
 * 统一的课程时间表定义
 * 整合了 ScheduleWidgetProvider.courseSchedule 和 CourseDetailDialog.getTimeRangeText 中的重复定义
 */
object CourseScheduleTimes {

    /**
     * 课程时间数据类
     */
    data class PeriodTimeSlot(
        val period: Int,
        val startHour: Int,
        val startMinute: Int,
        val endHour: Int,
        val endMinute: Int
    ) {
        val startTimeString: String
            get() = "${startHour}:${startMinute.toString().padStart(2, '0')}"

        val endTimeString: String
            get() = "${endHour}:${endMinute.toString().padStart(2, '0')}"

        val timeRangeString: String
            get() = "$startTimeString-$endTimeString"

        val startMinuteOfDay: Int
            get() = startHour * 60 + startMinute

        val endMinuteOfDay: Int
            get() = endHour * 60 + endMinute
    }

    /**
     * 10节课的时间表（完整版，用于小部件和详情弹窗）
     */
    val fullSchedule: Map<Int, PeriodTimeSlot> = mapOf(
        1  to PeriodTimeSlot(1, 8, 30, 9, 15),
        2  to PeriodTimeSlot(2, 9, 20, 10, 5),
        3  to PeriodTimeSlot(3, 10, 25, 11, 10),
        4  to PeriodTimeSlot(4, 11, 15, 12, 0),
        5  to PeriodTimeSlot(5, 14, 0, 14, 45),
        6  to PeriodTimeSlot(6, 14, 50, 15, 35),
        7  to PeriodTimeSlot(7, 15, 55, 16, 40),
        8  to PeriodTimeSlot(8, 16, 45, 17, 30),
        9  to PeriodTimeSlot(9, 19, 0, 19, 45),
        10 to PeriodTimeSlot(10, 19, 50, 20, 35)
    )

    /**
     * 获取指定节次的时间范围文本
     * @param startPeriod 开始节次
     * @param endPeriod 结束节次
     * @return 格式如 "08:30-10:05"，如果找不到则返回 "第X-Y节"
     */
    fun getTimeRangeText(startPeriod: Int, endPeriod: Int): String {
        val startSlot = fullSchedule[startPeriod]
        val endSlot = fullSchedule[endPeriod]

        return if (startSlot != null && endSlot != null) {
            "${startSlot.startTimeString}-${endSlot.endTimeString}"
        } else {
            "第${startPeriod}-${endPeriod}节"
        }
    }

    /**
     * 获取指定节次课程的开始时间（分钟数）
     */
    fun getStartMinuteOfDay(period: Int): Int {
        return fullSchedule[period]?.startMinuteOfDay ?: 0
    }

    /**
     * 判断课程是否已结束
     */
    fun isCourseEnded(endPeriod: Int, currentHour: Int, currentMinute: Int): Boolean {
        val endSlot = fullSchedule[endPeriod] ?: return true
        return if (currentHour > endSlot.endHour) true
        else currentHour == endSlot.endHour && currentMinute >= endSlot.endMinute
    }

    /**
     * 判断课程是否已开始
     */
    fun isCourseStarted(startPeriod: Int, currentHour: Int, currentMinute: Int): Boolean {
        val startSlot = fullSchedule[startPeriod] ?: return false
        return if (currentHour > startSlot.startHour) true
        else currentHour == startSlot.startHour && currentMinute >= startSlot.startMinute
    }

    /**
     * 获取课程时间字符串（用于小部件）
     */
    fun getCourseTimeString(startPeriod: Int, endPeriod: Int): String {
        val startSlot = fullSchedule[startPeriod] ?: return "00:00-00:00"
        val endSlot = fullSchedule[endPeriod] ?: return "00:00-00:00"
        return "${startSlot.startHour}:${startSlot.startMinute.toString().padStart(2, '0')}-" +
               "${endSlot.endHour}:${endSlot.endMinute.toString().padStart(2, '0')}"
    }
}
