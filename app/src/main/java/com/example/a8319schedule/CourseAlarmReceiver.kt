package com.example.a8319schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.a8319schedule.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 课程闹钟广播接收器
 * 处理上课提醒和每日课表摘要
 */
class CourseAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CourseAlarmReceiver"
        private const val CHANNEL_COURSE_REMINDER = "course_reminder_channel"
        private const val CHANNEL_DAILY_SUMMARY = "daily_summary_channel"
        private const val NOTIFICATION_ID_COURSE = 30000
        private const val NOTIFICATION_ID_DAILY = 40000
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到闹钟广播：${intent.action}")

        // 确保通知渠道存在
        createNotificationChannels(context)

        when (intent.action) {
            CourseAlarmScheduler.ACTION_COURSE_REMINDER -> {
                showCourseReminder(context, intent)
            }
            CourseAlarmScheduler.ACTION_DAILY_SUMMARY -> {
                showDailySummary(context)
            }
            Intent.ACTION_BOOT_COMPLETED,
            CourseAlarmScheduler.ACTION_BOOT_COMPLETED_RESCHEDULE -> {
                // 开机后重新调度闹钟
                CourseAlarmScheduler.rescheduleAll(context)
            }
        }
    }

    /**
     * 显示上课提醒通知
     */
    private fun showCourseReminder(context: Context, intent: Intent) {
        val courseName = intent.getStringExtra(CourseAlarmScheduler.EXTRA_COURSE_NAME) ?: "课程"
        val classroom = intent.getStringExtra(CourseAlarmScheduler.EXTRA_COURSE_CLASSROOM) ?: ""
        val period = intent.getStringExtra(CourseAlarmScheduler.EXTRA_COURSE_PERIOD) ?: ""
        val time = intent.getStringExtra(CourseAlarmScheduler.EXTRA_COURSE_TIME) ?: ""

        // ========== 上课提醒通知内容（可修改） ==========
        val title = "\uD83D\uDE21\uD83D\uDE21别睡了，上课了"          // 通知标题
        val content = buildString {
            append(courseName)          // 课程名称
            if (classroom.isNotBlank()) append(" · $classroom")  // 教室（如有）
            append("\n$period $time")   // 节次 + 上课时间
        }
        // ========== 上课提醒通知内容结束 ==========

        val notification = NotificationCompat.Builder(context, CHANNEL_COURSE_REMINDER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 用课程名哈希做 ID，避免多门课通知互相覆盖
        val notificationId = NOTIFICATION_ID_COURSE + (courseName.hashCode() and 0xFFFF)
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "显示上课提醒：$courseName")

        // 重新调度明天的提醒（如果今天已经提醒完，为明天注册）
        CourseAlarmScheduler.rescheduleAll(context)
    }

    /**
     * 显示每日课表摘要通知
     */
    private fun showDailySummary(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = CourseDatabase.getDatabase(context)
                val scheduleInfoDao = db.scheduleInfoDao()
                val courseDao = db.courseDao()
                val activeSchedule = scheduleInfoDao.getActiveSchedule()
                val activeScheduleId = activeSchedule?.id ?: 1L

                val currentWeek = WeekCalculator.calculateCurrentWeekForSchedule(activeSchedule)
                val calendar = Calendar.getInstance()
                val javaDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val ourDayOfWeek = if (javaDayOfWeek == Calendar.SUNDAY) 7 else javaDayOfWeek - 1

                val courses = courseDao.getCoursesByScheduleIdAndDay(activeScheduleId, ourDayOfWeek).first()
                    .filter { it.weekNumber == currentWeek }
                    .sortedBy { it.startPeriod }

                val dayNames = arrayOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                val dayName = dayNames.getOrNull(ourDayOfWeek) ?: "今天"

                // ========== 每日摘要通知内容（可修改） ==========
                val title = if (courses.isEmpty()) {
                    "$dayName 没有课程"       // 无课时的标题
                } else {
                    "$dayName 共${courses.size}节课"  // 有课时的标题
                }

                val content = if (courses.isEmpty()) {
                    "今天没有课，及时行乐吧"  // 无课时的内容
                } else {
                    courses.joinToString("\n") { course ->  // 有课时：每门课一行
                        val time = CourseScheduleTimes.getTimeRangeText(course.startPeriod, course.endPeriod)
                        buildString {
                            append("${course.name} $time")  // 课程名 + 时间
                            if (course.classroom.isNotBlank()) append(" · ${course.classroom}")  // 教室（如有）
                        }
                    }
                }
                // ========== 每日摘要通知内容结束 ==========

                val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()

                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID_DAILY, notification)

                Log.d(TAG, "显示每日摘要：$title")
            } catch (e: Exception) {
                Log.e(TAG, "显示每日摘要失败", e)
            }
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 上课提醒渠道 - 高优先级
            val reminderChannel = NotificationChannel(
                CHANNEL_COURSE_REMINDER,
                "上课提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "在课程开始前提醒你"
                enableVibration(true)
                setShowBadge(true)
            }

            // 每日摘要渠道 - 默认优先级
            val summaryChannel = NotificationChannel(
                CHANNEL_DAILY_SUMMARY,
                "每日课表摘要",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "每天早上推送今日课程概览"
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(reminderChannel, summaryChannel))
        }
    }
}
