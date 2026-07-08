package com.example.a8319schedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.data.CourseDatabase
import com.example.a8319schedule.data.CourseScheduleTimes
import com.example.a8319schedule.data.WeekCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import android.util.Log

/**
 * 小部件尺寸枚举
 */
enum class WidgetSize {
    SMALL,   // 2×2 (110×110dp)
    MEDIUM,  // 4×2 (300×110dp)
    LARGE    // 4×4 (300×250dp)
}

/**
 * 4×2中部件 Provider
 * 同时包含所有尺寸的共享逻辑
 */
open class ScheduleWidgetProvider : AppWidgetProvider() {
    
    companion object {
        const val TODAY_COURSES_WIDGET = 1
        
        private const val ACTION_MIUI_WIDGET_REFRESH = "miui.widget.action.MIUI_WIDGET_REFRESH"
        
        // 使用受管理的 CoroutineScope，避免泄漏
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        
        // 防抖动时间阈值（毫秒）
        private var lastUpdateTime = 0L
        
        open fun getWidgetSize(): WidgetSize = WidgetSize.MEDIUM
        
        fun forceUpdateAllWidgets(context: Context) {
            lastUpdateTime = 0L
            performUpdateAllWidgets(context)
        }
        
        fun updateAllWidgets(context: Context) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime < 1000) return
            lastUpdateTime = currentTime
            performUpdateAllWidgets(context)
        }
        
        private fun performUpdateAllWidgets(context: Context) {
            try {
                widgetScope.launch {
                    try {
                        withContext(Dispatchers.Main) {
                            val appWidgetManager = AppWidgetManager.getInstance(context)
                            updateWidgetsForProvider(context, appWidgetManager, ScheduleWidgetProvider::class.java, WidgetSize.MEDIUM)
                            updateWidgetsForProvider(context, appWidgetManager, ScheduleWidgetProviderSmall::class.java, WidgetSize.SMALL)
                            updateWidgetsForProvider(context, appWidgetManager, ScheduleWidgetProviderLarge::class.java, WidgetSize.LARGE)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ScheduleWidget", "performUpdateAllWidgets failed", e)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScheduleWidget", "performUpdateAllWidgets launch failed", e)
            }
        }
        
        private suspend fun updateWidgetsForProvider(
            context: Context, 
            appWidgetManager: AppWidgetManager, 
            providerClass: Class<*>, 
            size: WidgetSize
        ) {
            try {
                val componentName = ComponentName(context, providerClass)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                appWidgetIds.forEach { appWidgetId ->
                    updateAppWidgetForSize(context, appWidgetManager, appWidgetId, size)
                }
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "updateWidgetsForProvider failed for $providerClass", e)
            }
        }
        
        suspend fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, fallbackSize: WidgetSize = WidgetSize.MEDIUM) = withContext(Dispatchers.IO) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val size = determineWidgetSize(options, fallbackSize)
            updateAppWidgetForSize(context, appWidgetManager, appWidgetId, size)
        }
        
        private fun determineWidgetSize(options: Bundle?, fallbackSize: WidgetSize): WidgetSize {
            val minWidth = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            if (minWidth > 0 && minHeight > 0) {
                return when {
                    minWidth <= 150 -> WidgetSize.SMALL
                    minHeight >= 200 -> WidgetSize.LARGE
                    else -> WidgetSize.MEDIUM
                }
            }
            return fallbackSize
        }
        
        private suspend fun updateAppWidgetForSize(
            context: Context, 
            appWidgetManager: AppWidgetManager, 
            appWidgetId: Int, 
            size: WidgetSize
        ) = withContext(Dispatchers.IO) {
            try {
                val views = createBaseWidgetView(context, appWidgetId, size)
                
                val currentTime = Calendar.getInstance()
                val dayOfWeek = when (currentTime.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "周一"
                    Calendar.TUESDAY -> "周二"
                    Calendar.WEDNESDAY -> "周三"
                    Calendar.THURSDAY -> "周四"
                    Calendar.FRIDAY -> "周五"
                    Calendar.SATURDAY -> "周六"
                    Calendar.SUNDAY -> "周日"
                    else -> "周一"
                }
                
                when (size) {
                    WidgetSize.SMALL -> {
                        views.setTextViewText(R.id.widget_title, "今日课表")
                        views.setTextViewText(R.id.day_of_week, dayOfWeek)
                        views.setTextViewText(R.id.no_courses_text, "正在加载...")
                        views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                        views.setViewVisibility(R.id.course_info_layout, View.GONE)
                    }
                    WidgetSize.MEDIUM -> {
                        views.setTextViewText(R.id.widget_title, "今日课程")
                        views.setTextViewText(R.id.day_of_week, dayOfWeek)
                        views.setTextViewText(R.id.no_courses_text, "正在加载...")
                        views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                        views.setViewVisibility(R.id.courses_list, View.GONE)
                    }
                    WidgetSize.LARGE -> {
                        views.setTextViewText(R.id.widget_title, "今日课程")
                        views.setTextViewText(R.id.day_of_week, dayOfWeek)
                        views.setTextViewText(R.id.no_courses_text, "正在加载...")
                        views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                        views.setViewVisibility(R.id.courses_list, View.GONE)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
                
                val courses = getCurrentDayCourses(context)
                updateCourseContent(views, courses, currentTime, size)
                
                withContext(Dispatchers.Main) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
                
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "updateAppWidgetForSize failed", e)
                showErrorView(context, appWidgetManager, appWidgetId, size)
            }
        }
        
        private fun createBaseWidgetView(context: Context, appWidgetId: Int, size: WidgetSize): RemoteViews {
            val layoutId = when (size) {
                WidgetSize.SMALL -> R.layout.widget_small
                WidgetSize.MEDIUM -> R.layout.widget_today_courses
                WidgetSize.LARGE -> R.layout.widget_large
            }
            val views = RemoteViews(context.packageName, layoutId)
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("from_widget", true)
            }
            
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            
            val pendingIntent = PendingIntent.getActivity(context, appWidgetId, intent, flags)
            views.setOnClickPendingIntent(android.R.id.background, pendingIntent)
            
            return views
        }
        
        private suspend fun showErrorView(
            context: Context, 
            appWidgetManager: AppWidgetManager, 
            appWidgetId: Int, 
            size: WidgetSize
        ) {
            try {
                val views = createBaseWidgetView(context, appWidgetId, size)
                when (size) {
                    WidgetSize.SMALL -> {
                        views.setTextViewText(R.id.no_courses_text, "加载失败")
                        views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                        views.setViewVisibility(R.id.course_info_layout, View.GONE)
                    }
                    WidgetSize.MEDIUM, WidgetSize.LARGE -> {
                        views.setTextViewText(R.id.no_courses_text, "加载课程失败，请打开应用导入课表")
                        views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                        views.setViewVisibility(R.id.courses_list, View.GONE)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "showErrorView failed", e)
            }
        }
        
        private suspend fun getCurrentDayCourses(context: Context): List<Course> = withContext(Dispatchers.IO) {
            try {
                val database = CourseDatabase.getDatabase(context)
                val courseDao = database.courseDao()
                val scheduleInfoDao = database.scheduleInfoDao()
                
                val calendar = Calendar.getInstance()
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                
                val activeSchedule = scheduleInfoDao.getActiveSchedule()
                val activeScheduleId = activeSchedule?.id ?: 1L
                
                // 使用统一的 WeekCalculator 计算当前周次
                val weekNumber = try {
                    WeekCalculator.calculateCurrentWeekForSchedule(activeSchedule)
                } catch (e: Exception) {
                    Log.w("ScheduleWidget", "Week calculation failed, fallback to week 1", e)
                    1
                }
                
                val ourDayOfWeek = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1
                
                val coursesFlow = courseDao.getCoursesByScheduleIdAndDay(activeScheduleId, ourDayOfWeek)
                val coursesList = coursesFlow.first()
                
                val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                val currentMinute = calendar.get(Calendar.MINUTE)
                
                Log.d("ScheduleWidget", "Query: scheduleId=$activeScheduleId, week=$weekNumber, day=$ourDayOfWeek, totalCourses=${coursesList.size}")
                
                // 使用 CourseScheduleTimes 判断课程是否已结束
                val result = coursesList
                    .filter { course -> 
                        val matchWeek = weekNumber == course.weekNumber
                        val notEnded = !CourseScheduleTimes.isCourseEnded(course.endPeriod, currentHour, currentMinute)
                        matchWeek && notEnded 
                    }
                    .sortedBy { course -> CourseScheduleTimes.getStartMinuteOfDay(course.startPeriod) }
                
                Log.d("ScheduleWidget", "Filtered: ${result.size} active courses (weekMatch filter applied)")
                result
                
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "getCurrentDayCourses failed", e)
                emptyList()
            }
        }
        
        private fun updateCourseContent(views: RemoteViews, courses: List<Course>, currentTime: Calendar, size: WidgetSize) {
            try {
                when (size) {
                    WidgetSize.SMALL -> updateSmallCourseContent(views, courses, currentTime)
                    WidgetSize.MEDIUM -> updateMediumCourseContent(views, courses, currentTime)
                    WidgetSize.LARGE -> updateLargeCourseContent(views, courses, currentTime)
                }
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "updateCourseContent failed", e)
                when (size) {
                    WidgetSize.SMALL -> {
                        views.setTextViewText(R.id.no_courses_text, "加载课程失败")
                        views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                        views.setViewVisibility(R.id.course_info_layout, View.GONE)
                    }
                    WidgetSize.MEDIUM, WidgetSize.LARGE -> {
                        views.setTextViewText(R.id.no_courses_text, "加载课程失败")
                        views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                        views.setViewVisibility(R.id.courses_list, View.GONE)
                    }
                }
            }
        }
        
        private fun updateSmallCourseContent(views: RemoteViews, courses: List<Course>, currentTime: Calendar) {
            if (courses.isEmpty()) {
                views.setTextViewText(R.id.no_courses_text, "今天没有课程")
                views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                views.setViewVisibility(R.id.course_info_layout, View.GONE)
                return
            }
            
            views.setViewVisibility(R.id.no_courses_text, View.GONE)
            views.setViewVisibility(R.id.course_info_layout, View.VISIBLE)
            
            val course = courses.first()
            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
            val currentMinute = currentTime.get(Calendar.MINUTE)
            val courseStarted = CourseScheduleTimes.isCourseStarted(course.startPeriod, currentHour, currentMinute)
            
            views.setTextViewText(R.id.course_name_small, course.name)
            
            if (courseStarted) {
                views.setTextViewText(R.id.course_status_small, "进行中 · ${course.startPeriod}-${course.endPeriod}节")
                views.setTextViewText(R.id.course_location_small, course.classroom)
            } else {
                views.setTextViewText(R.id.course_status_small, CourseScheduleTimes.getCourseTimeString(course.startPeriod, course.endPeriod))
                val locationText = if (course.teacher.isNotBlank()) {
                    "${course.classroom} | ${course.teacher}"
                } else {
                    course.classroom
                }
                views.setTextViewText(R.id.course_location_small, locationText)
            }
        }
        
        private fun updateMediumCourseContent(views: RemoteViews, courses: List<Course>, currentTime: Calendar) {
            if (courses.isEmpty()) {
                views.setTextViewText(R.id.no_courses_text, "今天没有课程")
                views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                views.setViewVisibility(R.id.courses_list, View.GONE)
                return
            }
            
            views.setViewVisibility(R.id.no_courses_text, View.GONE)
            views.setViewVisibility(R.id.courses_list, View.VISIBLE)
            
            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
            val currentMinute = currentTime.get(Calendar.MINUTE)
            val displayCourses = courses.take(3)
            
            for ((index, course) in displayCourses.withIndex()) {
                setCourseInfoMedium(views, course, index + 1, currentHour, currentMinute)
            }
            
            for (i in displayCourses.size..2) {
                views.setViewVisibility(
                    when (i) {
                        0 -> R.id.course_1
                        1 -> R.id.course_2
                        else -> R.id.course_3
                    }, View.GONE
                )
            }
        }
        
        private fun setCourseInfoMedium(views: RemoteViews, course: Course, index: Int, currentHour: Int, currentMinute: Int) {
            try {
                val courseStarted = CourseScheduleTimes.isCourseStarted(course.startPeriod, currentHour, currentMinute)
                
                val nameId = when (index) { 1 -> R.id.course_name_1; 2 -> R.id.course_name_2; else -> R.id.course_name_3 }
                val periodId = when (index) { 1 -> R.id.course_period_1; 2 -> R.id.course_period_2; else -> R.id.course_period_3 }
                val timeId = when (index) { 1 -> R.id.course_time_range_1; 2 -> R.id.course_time_range_2; else -> R.id.course_time_range_3 }
                val locationId = when (index) { 1 -> R.id.course_location_1; 2 -> R.id.course_location_2; else -> R.id.course_location_3 }
                val courseId = when (index) { 1 -> R.id.course_1; 2 -> R.id.course_2; else -> R.id.course_3 }
                
                views.setTextViewText(nameId, course.name)
                views.setTextViewText(periodId, if (courseStarted) "进行中" else "未开始")
                
                if (courseStarted) {
                    views.setTextViewText(timeId, "")
                    views.setTextViewText(locationId, "${course.startPeriod}-${course.endPeriod}节")
                } else {
                    views.setTextViewText(timeId, CourseScheduleTimes.getCourseTimeString(course.startPeriod, course.endPeriod))
                    val locationText = if (course.teacher.isNotBlank()) "${course.classroom} | ${course.teacher}" else course.classroom
                    views.setTextViewText(locationId, locationText)
                }
                
                views.setViewVisibility(courseId, View.VISIBLE)
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "setCourseInfoMedium failed for index $index", e)
            }
        }
        
        private fun updateLargeCourseContent(views: RemoteViews, courses: List<Course>, currentTime: Calendar) {
            if (courses.isEmpty()) {
                views.setTextViewText(R.id.no_courses_text, "今天没有课程")
                views.setViewVisibility(R.id.no_courses_text, View.VISIBLE)
                views.setViewVisibility(R.id.courses_list, View.GONE)
                return
            }
            
            views.setViewVisibility(R.id.no_courses_text, View.GONE)
            views.setViewVisibility(R.id.courses_list, View.VISIBLE)
            
            val currentHour = currentTime.get(Calendar.HOUR_OF_DAY)
            val currentMinute = currentTime.get(Calendar.MINUTE)
            val displayCourses = courses.take(5)
            
            for ((index, course) in displayCourses.withIndex()) {
                setCourseInfoLarge(views, course, index + 1, currentHour, currentMinute)
            }
            
            for (i in displayCourses.size..4) {
                val courseId = when (i) {
                    0 -> R.id.course_1
                    1 -> R.id.course_2
                    2 -> R.id.course_3
                    3 -> R.id.course_4
                    else -> R.id.course_5
                }
                views.setViewVisibility(courseId, View.GONE)
            }
        }
        
        private fun setCourseInfoLarge(views: RemoteViews, course: Course, index: Int, currentHour: Int, currentMinute: Int) {
            try {
                val courseStarted = CourseScheduleTimes.isCourseStarted(course.startPeriod, currentHour, currentMinute)
                
                val nameId = when (index) {
                    1 -> R.id.course_name_1; 2 -> R.id.course_name_2; 3 -> R.id.course_name_3
                    4 -> R.id.course_name_4; else -> R.id.course_name_5
                }
                val periodId = when (index) {
                    1 -> R.id.course_period_1; 2 -> R.id.course_period_2; 3 -> R.id.course_period_3
                    4 -> R.id.course_period_4; else -> R.id.course_period_5
                }
                val timeId = when (index) {
                    1 -> R.id.course_time_range_1; 2 -> R.id.course_time_range_2; 3 -> R.id.course_time_range_3
                    4 -> R.id.course_time_range_4; else -> R.id.course_time_range_5
                }
                val locationId = when (index) {
                    1 -> R.id.course_location_1; 2 -> R.id.course_location_2; 3 -> R.id.course_location_3
                    4 -> R.id.course_location_4; else -> R.id.course_location_5
                }
                val courseId = when (index) {
                    1 -> R.id.course_1; 2 -> R.id.course_2; 3 -> R.id.course_3
                    4 -> R.id.course_4; else -> R.id.course_5
                }
                
                views.setTextViewText(nameId, course.name)
                views.setTextViewText(periodId, if (courseStarted) "进行中" else "未开始")
                
                if (courseStarted) {
                    views.setTextViewText(timeId, "")
                    views.setTextViewText(locationId, "${course.startPeriod}-${course.endPeriod}节")
                } else {
                    views.setTextViewText(timeId, CourseScheduleTimes.getCourseTimeString(course.startPeriod, course.endPeriod))
                    val locationText = if (course.teacher.isNotBlank()) "${course.classroom} | ${course.teacher}" else course.classroom
                    views.setTextViewText(locationId, locationText)
                }
                
                views.setViewVisibility(courseId, View.VISIBLE)
            } catch (e: Exception) {
                Log.e("ScheduleWidget", "setCourseInfoLarge failed for index $index", e)
            }
        }
    }
    
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val fallbackSize = when (this) {
            is ScheduleWidgetProviderSmall -> WidgetSize.SMALL
            is ScheduleWidgetProviderLarge -> WidgetSize.LARGE
            else -> WidgetSize.MEDIUM
        }
        Log.d("ScheduleWidget", "onUpdate called, ids=${appWidgetIds.toList()}, size=$fallbackSize")
        for (appWidgetId in appWidgetIds) {
            widgetScope.launch {
                try {
                    updateAppWidget(context, appWidgetManager, appWidgetId, fallbackSize)
                } catch (e: Exception) {
                    Log.e("ScheduleWidget", "onUpdate failed for widget $appWidgetId", e)
                }
            }
        }
    }
    
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d("ScheduleWidget", "onEnabled: first widget added, starting periodic update")
        try {
            SimpleWidgetUpdater.startPeriodicUpdate(context.applicationContext)
        } catch (e: Exception) {
            Log.e("ScheduleWidget", "Failed to start periodic update", e)
        }
        updateAllWidgets(context)
    }
    
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d("ScheduleWidget", "onDisabled: last widget removed, stopping periodic update")
        try {
            SimpleWidgetUpdater.stopPeriodicUpdate(context.applicationContext)
        } catch (e: Exception) {
            Log.e("ScheduleWidget", "Failed to stop periodic update", e)
        }
    }
    
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
    }
    
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (newOptions?.containsKey("miuiIdChanged") == true) {
            lastUpdateTime = 0L
        }
        onUpdate(context, appWidgetManager, intArrayOf(appWidgetId))
    }
    
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        lastUpdateTime = 0L
        updateAllWidgets(context)
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_MIUI_WIDGET_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, this::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}

class ScheduleWidgetProviderSmall : ScheduleWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}

class ScheduleWidgetProviderLarge : ScheduleWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
    }
}
