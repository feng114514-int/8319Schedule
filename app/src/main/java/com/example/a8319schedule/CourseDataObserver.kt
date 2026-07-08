package com.example.a8319schedule

import android.content.Context

import com.example.a8319schedule.data.CourseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 课表数据变化观察者
 * 监听课程数据库的增删改操作，自动触发小部件更新
 */
class CourseDataObserver(private val context: Context) {
    
    private var observationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * 注册观察者
     */
    fun register() {
        try {
            val database = CourseDatabase.getDatabase(context)
            val courseDao = database.courseDao()
            
            // 使用Flow监听数据库变化
            observationJob = scope.launch {
                try {
                    // 直接监听所有课表的数据变化，Room Flow 会在数据变更时自动触发
                    // 无需延迟，Flow 本身保证了数据的实时性
                    courseDao.getAllCourses().collect {
                        triggerWidgetUpdate()
                    }
                } catch (e: Exception) {
                    // 忽略异常
                }
            }
        } catch (e: Exception) {
            // 忽略异常
        }
    }
    
    /**
     * 取消注册观察者
     */
    fun unregister() {
        try {
            observationJob?.cancel()
            observationJob = null
        } catch (e: Exception) {
            // 忽略异常
        }
    }
    
    /**
     * 触发小部件更新
     */
    private fun triggerWidgetUpdate() {
        try {
            ScheduleWidgetProvider.updateAllWidgets(context)
        } catch (e: Exception) {
            // 忽略异常
        }
    }
    
    companion object {
        @Volatile
        private var instance: CourseDataObserver? = null
        
        /**
         * 获取单例实例并注册
         */
        fun getInstanceAndRegister(context: Context): CourseDataObserver {
            return instance ?: synchronized(this) {
                instance ?: CourseDataObserver(context.applicationContext).also { observer ->
                    observer.register()
                    instance = observer
                }
            }
        }
        
        /**
         * 取消注册并清理
         */
        fun unregisterInstance(context: Context) {
            instance?.unregister()
            instance = null
        }
    }
}
