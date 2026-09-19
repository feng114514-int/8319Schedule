package com.example.a8319schedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a8319schedule.data.CourseDatabase
import com.example.a8319schedule.data.CourseRepository
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.data.ScheduleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 课表管理ViewModel
 */
class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    
    private val db = CourseDatabase.getDatabase(application)
    private val scheduleRepo = ScheduleRepository(db.scheduleInfoDao(), db.courseDao())
    private val courseRepo = CourseRepository(db.courseDao())
    
    // 课表列表
    private val _schedules = MutableStateFlow<List<ScheduleInfo>>(emptyList())
    val schedules: StateFlow<List<ScheduleInfo>> = _schedules.asStateFlow()
    
    // 当前激活的课表
    private val _activeSchedule = MutableStateFlow<ScheduleInfo?>(null)
    val activeSchedule: StateFlow<ScheduleInfo?> = _activeSchedule.asStateFlow()
    
    // 当前课表ID
    private val _activeScheduleId = MutableStateFlow(1L) // 默认为1
    val activeScheduleId: StateFlow<Long> = _activeScheduleId.asStateFlow()
    
    init {
        viewModelScope.launch {
            // 收集所有课表
            scheduleRepo.getAllSchedules().collect { scheduleList ->
                _schedules.value = scheduleList
                
                // 如果没有激活的课表且课表列表不为空，激活第一个课表
                if (_activeSchedule.value == null && scheduleList.isNotEmpty()) {
                    activateSchedule(scheduleList.first().id)
                }
                // 如果没有任何课表，创建一个默认课表
                else if (_activeSchedule.value == null && scheduleList.isEmpty()) {
                    createSchedule("默认课表", "应用默认课表")
                }
            }
        }
        
        // 获取当前激活的课表
        viewModelScope.launch {
            val active = scheduleRepo.getActiveSchedule()
            _activeSchedule.value = active
            _activeScheduleId.value = active?.id ?: 1L
        }
    }
    
    /**
     * 创建新课表并返回新课表ID（可等待版本）。
     *
     * 导入流程必须用它：只有拿到新建课表的真实ID之后才能写入课程。
     * 若调用异步的 createSchedule 后再读 activeScheduleId，会读到旧课表ID，
     * 导致"课表新建成功，但课程覆盖到了原课表"。
     */
    suspend fun createScheduleAndGetId(name: String, description: String = ""): Long {
        val newId = scheduleRepo.createSchedule(name, description)
        _activeScheduleId.value = newId
        _activeSchedule.value = scheduleRepo.getScheduleById(newId)
        return newId
    }

    /**
     * 创建新课表（异步版本，供不需要ID的调用点使用）
     */
    fun createSchedule(name: String, description: String = "") {
        viewModelScope.launch {
            createScheduleAndGetId(name, description)
        }
    }
    
    /**
     * 激活指定课表（可等待版本）
     */
    suspend fun activateScheduleAndWait(scheduleId: Long) {
        scheduleRepo.activateSchedule(scheduleId)
        _activeScheduleId.value = scheduleId
        _activeSchedule.value = scheduleRepo.getScheduleById(scheduleId)
        
        // 切换课表后，立即更新小部件
        com.example.a8319schedule.ScheduleWidgetProvider.updateAllWidgets(getApplication())
        com.example.a8319schedule.ScheduleWidgetProvider.forceUpdateAllWidgets(getApplication())
    }

    /**
     * 激活指定课表
     */
    fun activateSchedule(scheduleId: Long) {
        viewModelScope.launch {
            activateScheduleAndWait(scheduleId)
        }
    }
    
    /**
     * 删除课表
     */
    fun deleteSchedule(schedule: ScheduleInfo) {
        viewModelScope.launch {
            scheduleRepo.deleteSchedule(schedule)
            
            // 如果删除的是当前激活的课表，需要切换到其他课表
            if (schedule.id == _activeScheduleId.value) {
                val remainingSchedules = _schedules.value.filter { it.id != schedule.id }
                if (remainingSchedules.isNotEmpty()) {
                    activateSchedule(remainingSchedules.first().id)
                } else {
                    // 如果没有其他课表了，创建一个新的默认课表
                    createSchedule("默认课表", "新建的默认课表")
                }
            }
        }
    }
    
    /**
     * 更新课表信息
     */
    fun updateSchedule(schedule: ScheduleInfo) {
        viewModelScope.launch {
            scheduleRepo.updateSchedule(schedule)
        }
    }
    
    /**
     * 复制课表
     */
    fun duplicateSchedule(sourceScheduleId: Long, newName: String) {
        viewModelScope.launch {
            val newId = scheduleRepo.duplicateSchedule(sourceScheduleId, newName)
            if (newId != null) {
                _activeScheduleId.value = newId
                _activeSchedule.value = scheduleRepo.getScheduleById(newId)
            }
        }
    }
    
    /**
     * 获取当前课表的课程仓库
     */
    fun getCurrentCourseRepository(): CourseRepository {
        return courseRepo
    }
    
    /**
     * 获取指定课表的课程
     */
    fun getCoursesForSchedule(scheduleId: Long) = courseRepo.getCoursesByScheduleId(scheduleId)
    
    /**
     * 更新课表的开学日期（可等待版本）
     */
    suspend fun updateStartDateAndWait(scheduleId: Long, startDate: Long) {
        android.util.Log.d("ScheduleViewModel", "开始更新课表ID: $scheduleId 的开学日期为: $startDate")
        scheduleRepo.updateStartDate(scheduleId, startDate)
        
        // 获取更新后的课表信息
        val updatedSchedule = scheduleRepo.getScheduleById(scheduleId)
        android.util.Log.d("ScheduleViewModel", "数据库中的更新结果: ${updatedSchedule?.name}, ID: ${updatedSchedule?.id}, 开学日期: ${updatedSchedule?.startDate}")
        
        // 如果更新的是当前激活的课表，也需要更新 ViewModel 中的状态
        if (scheduleId == _activeScheduleId.value) {
            _activeSchedule.value = updatedSchedule
            android.util.Log.d("ScheduleViewModel", "更新了ViewModel中的激活课表状态")
        } else {
            android.util.Log.d("ScheduleViewModel", "更新的不是当前激活的课表，不更新ViewModel状态")
        }
    }

    /**
     * 更新课表的开学日期
     */
    fun updateStartDate(scheduleId: Long, startDate: Long) {
        viewModelScope.launch {
            updateStartDateAndWait(scheduleId, startDate)
        }
    }
}