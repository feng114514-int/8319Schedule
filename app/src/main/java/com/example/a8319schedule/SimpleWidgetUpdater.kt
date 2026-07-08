package com.example.a8319schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit


/**
 * 小部件定时刷新器
 * 使用 WorkManager 定期触发小部件更新，替代小米曝光刷新
 */
class SimpleWidgetUpdater {
    companion object {
        private const val ACTION_UPDATE_WIDGET = "com.example.a8319schedule.UPDATE_WIDGET"
        private const val WORK_NAME = "widget_periodic_refresh"
        // 刷新间隔：15分钟（WorkManager 最小间隔为15分钟）
        private const val REFRESH_INTERVAL_MINUTES = 15L
        
        /**
         * 启动定期更新（WorkManager 方式，比 AlarmManager 更省电更稳定）
         */
        fun startPeriodicUpdate(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,  // 使用 REPLACE 确保任务总是被更新
                    workRequest
                )
        }
        
        /**
         * 停止定期更新
         */
        fun stopPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context.applicationContext)
                .cancelUniqueWork(WORK_NAME)
        }
    }
    
    /**
     * WorkManager Worker：执行小部件刷新
     */
    class WidgetRefreshWorker(
        context: Context,
        params: WorkerParameters
    ) : Worker(context, params) {
        override fun doWork(): Result {
            return try {
                android.util.Log.d("ScheduleWidget", "WorkManager: performing periodic refresh")
                ScheduleWidgetProvider.updateAllWidgets(applicationContext)
                Result.success()
            } catch (e: Exception) {
                android.util.Log.e("ScheduleWidget", "WorkManager: periodic refresh failed", e)
                Result.retry()
            }
        }
    }
    
    /**
     * 小部件更新广播接收器（保留兼容旧版 AlarmManager 触发）
     */
    class WidgetUpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.action == ACTION_UPDATE_WIDGET) {
                try {
                    ScheduleWidgetProvider.updateAllWidgets(context)
                } catch (e: Exception) {
                    // 忽略异常
                }
            }
        }
    }
}
