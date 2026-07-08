package com.example.a8319schedule

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WidgetRefreshService : Service() {
    private var refreshJob: Job? = null
    private val refreshInterval = 60000L // 60秒刷新一次
    
    override fun onCreate() {
        super.onCreate()
        
        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "widget_refresh_channel",
                "小部件刷新服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于定期刷新小部件数据"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        
        // 创建前台通知
        val notification = createNotification()
        startForeground(1, notification)
        
        startPeriodicRefresh()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // 服务被杀死后自动重启
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicRefresh()
    }
    
    private fun startPeriodicRefresh() {
        refreshJob = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                try {
                    // 调用小部件更新
                    ScheduleWidgetProvider.updateAllWidgets(this@WidgetRefreshService)
                    
                    // 等待下次刷新
                    delay(refreshInterval)
                } catch (e: Exception) {
                    delay(refreshInterval)
                }
            }
        }
    }
    
    private fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, "widget_refresh_channel")
                .setContentTitle("课表小部件")
                .setContentText("小部件正在后台更新数据")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("课表小部件")
                .setContentText("小部件正在后台更新数据")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build()
        }
    }
    
    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, WidgetRefreshService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, WidgetRefreshService::class.java)
            context.stopService(intent)
        }
    }
}