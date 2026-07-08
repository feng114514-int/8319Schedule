package com.example.a8319schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.a8319schedule.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 课程闹钟调度器
 * 负责注册/取消 AlarmManager 闹钟
 */
object CourseAlarmScheduler {

    private const val TAG = "CourseAlarmScheduler"

    // 请求码基础值
    private const val REQUEST_BASE_COURSE = 10000   // 上课提醒
    private const val REQUEST_BASE_DAILY = 20000    // 每日摘要

    // Action
    const val ACTION_COURSE_REMINDER = "com.example.a8319schedule.COURSE_REMINDER"
    const val ACTION_DAILY_SUMMARY = "com.example.a8319schedule.DAILY_SUMMARY"
    const val ACTION_BOOT_COMPLETED_RESCHEDULE = "com.example.a8319schedule.BOOT_RESCHEDULE"

    // Extra keys
    const val EXTRA_COURSE_NAME = "course_name"
    const val EXTRA_COURSE_CLASSROOM = "course_classroom"
    const val EXTRA_COURSE_PERIOD = "course_period"
    const val EXTRA_COURSE_TIME = "course_time"

    /**
     * 重新调度所有闹钟（启动时、设置变更时调用）
     */
    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val notifSettings = NotificationSettingsManager(context).settings.first()
                // 先取消所有
                cancelAll(context)
                // 重新注册
                if (notifSettings.courseReminderEnabled) {
                    scheduleCourseReminders(context, notifSettings.reminderMinutesBefore)
                }
                if (notifSettings.dailySummaryEnabled) {
                    scheduleDailySummary(
                        context,
                        notifSettings.dailySummaryHour,
                        notifSettings.dailySummaryMinute
                    )
                }
                Log.d(TAG, "闹钟重新调度完成：上课提醒=${notifSettings.courseReminderEnabled}，每日摘要=${notifSettings.dailySummaryEnabled}")
            } catch (e: Exception) {
                Log.e(TAG, "重新调度闹钟失败", e)
            }
        }
    }

    /**
     * 取消所有闹钟
     */
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // 取消上课提醒（最多20节课）
        for (i in 0 until 20) {
            val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
                action = ACTION_COURSE_REMINDER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_BASE_COURSE + i, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let { alarmManager.cancel(it) }
        }
        // 取消每日摘要
        val dailyIntent = Intent(context, CourseAlarmReceiver::class.java).apply {
            action = ACTION_DAILY_SUMMARY
        }
        val dailyPending = PendingIntent.getBroadcast(
            context, REQUEST_BASE_DAILY, dailyIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        dailyPending?.let { alarmManager.cancel(it) }
    }

    /**
     * 注册今天和明天的上课提醒闹钟
     */
    private suspend fun scheduleCourseReminders(context: Context, minutesBefore: Int) {
        val db = CourseDatabase.getDatabase(context)
        val scheduleInfoDao = db.scheduleInfoDao()
        val courseDao = db.courseDao()
        val activeSchedule = scheduleInfoDao.getActiveSchedule()
        val activeScheduleId = activeSchedule?.id ?: 1L

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 为今天和明天注册闹钟
        for (dayOffset in 0..1) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, dayOffset)
            }
            val javaDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            // 转换为我们的 dayOfWeek（1=周一...7=周日）
            val ourDayOfWeek = if (javaDayOfWeek == Calendar.SUNDAY) 7 else javaDayOfWeek - 1
            // 使用课表的开学日期计算对应日期的周次，正确处理跨周
            val targetWeek = WeekCalculator.calculateWeekForDateForSchedule(cal.timeInMillis, activeSchedule)

            val courses = courseDao.getCoursesByScheduleIdAndDay(activeScheduleId, ourDayOfWeek).first()
                .filter { it.weekNumber == targetWeek }
                .sortedBy { it.startPeriod }

            courses.forEachIndexed { index, course ->
                val timeSlot = CourseScheduleTimes.fullSchedule[course.startPeriod] ?: return@forEachIndexed

                // 计算闹钟时间：课程开始时间 - minutesBefore
                val alarmCal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, timeSlot.startHour)
                    set(Calendar.MINUTE, timeSlot.startMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MINUTE, -minutesBefore)
                    // 如果是明天的课，加一天
                    if (dayOffset == 1) add(Calendar.DAY_OF_MONTH, 1)
                }

                // 只注册未来的闹钟
                if (alarmCal.timeInMillis > System.currentTimeMillis()) {
                    val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
                        action = ACTION_COURSE_REMINDER
                        putExtra(EXTRA_COURSE_NAME, course.name)
                        putExtra(EXTRA_COURSE_CLASSROOM, course.classroom)
                        putExtra(EXTRA_COURSE_PERIOD, "${course.startPeriod}-${course.endPeriod}节")
                        putExtra(EXTRA_COURSE_TIME, CourseScheduleTimes.getTimeRangeText(course.startPeriod, course.endPeriod))
                    }

                    val requestCode = REQUEST_BASE_COURSE + dayOffset * 10 + index
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, requestCode, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    try {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            alarmCal.timeInMillis,
                            pendingIntent
                        )
                        Log.d(TAG, "注册上课提醒：${course.name} 在 ${alarmCal.time}")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "无法设置精确闹钟，尝试不精确闹钟", e)
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            alarmCal.timeInMillis,
                            pendingIntent
                        )
                    }
                }
            }
        }
    }

    /**
     * 注册每日课表摘要闹钟
     */
    private fun scheduleDailySummary(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 如果今天的时间已过，从明天开始
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val intent = Intent(context, CourseAlarmReceiver::class.java).apply {
            action = ACTION_DAILY_SUMMARY
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_BASE_DAILY, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 每天重复
        try {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            Log.d(TAG, "注册每日摘要：每天 $hour:$minute")
        } catch (e: SecurityException) {
            Log.e(TAG, "无法设置重复闹钟，使用精确闹钟", e)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
