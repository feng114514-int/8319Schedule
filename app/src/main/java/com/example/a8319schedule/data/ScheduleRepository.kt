package com.example.a8319schedule.data

import kotlinx.coroutines.flow.Flow

/**
 * 课表信息仓库类
 */
class ScheduleRepository(
    private val scheduleDao: ScheduleInfoDao,
    private val courseDao: CourseDao
) {
    
    /**
     * 获取所有课表
     */
    fun getAllSchedules(): Flow<List<ScheduleInfo>> = scheduleDao.getAllSchedules()
    
    /**
     * 获取当前激活的课表
     */
    suspend fun getActiveSchedule(): ScheduleInfo? = scheduleDao.getActiveSchedule()
    
    /**
     * 根据ID获取课表
     */
    suspend fun getScheduleById(id: Long): ScheduleInfo? = scheduleDao.getScheduleById(id)
    
    /**
     * 创建新课表
     */
    suspend fun createSchedule(name: String, description: String = ""): Long {
        // 先将所有课表设为非激活状态
        scheduleDao.deactivateAllSchedules()
        
        // 创建新课表并设为激活状态
        val schedule = ScheduleInfo(
            name = name,
            description = description,
            isActive = true
        )
        
        return scheduleDao.insertSchedule(schedule)
    }
    
    /**
     * 删除课表及其所有课程
     */
    suspend fun deleteSchedule(schedule: ScheduleInfo) {
        // 先删除该课表的所有课程
        courseDao.deleteCoursesByScheduleId(schedule.id)
        
        // 再删除课表信息
        scheduleDao.deleteSchedule(schedule)
    }
    
    /**
     * 激活指定课表
     */
    suspend fun activateSchedule(id: Long) {
        // 先将所有课表设为非激活状态
        scheduleDao.deactivateAllSchedules()
        
        // 激活指定课表
        scheduleDao.activateSchedule(id)
        
        // 不更新时间戳，保持原有顺序
    }
    
    /**
     * 更新课表信息
     */
    suspend fun updateSchedule(schedule: ScheduleInfo) {
        scheduleDao.updateSchedule(schedule)
    }
    
    /**
     * 更新课表的开学日期
     */
    suspend fun updateStartDate(scheduleId: Long, startDate: Long) {
        android.util.Log.d("ScheduleRepository", "更新课表ID: $scheduleId 的开学日期为: $startDate")
        scheduleDao.updateStartDate(scheduleId, startDate)
        
        // 验证更新是否成功
        val updatedSchedule = scheduleDao.getScheduleById(scheduleId)
        android.util.Log.d("ScheduleRepository", "更新后的课表: ${updatedSchedule?.name}, ID: ${updatedSchedule?.id}, 开学日期: ${updatedSchedule?.startDate}")
    }
    
    /**
     * 复制课表
     */
    suspend fun duplicateSchedule(sourceScheduleId: Long, newName: String): Long? {
        val sourceSchedule = scheduleDao.getScheduleById(sourceScheduleId) ?: return null
        
        // 先将所有课表设为非激活状态
        scheduleDao.deactivateAllSchedules()
        
        // 创建新课表
        val newSchedule = ScheduleInfo(
            name = newName,
            description = "复制自: ${sourceSchedule.name}",
            isActive = true
        )
        val newScheduleId = scheduleDao.insertSchedule(newSchedule)
        
        // 获取源课表的所有课程并复制
        val sourceCourses = courseDao.getCoursesByScheduleId(sourceScheduleId)
        sourceCourses.collect { courses ->
            // 复制课程
            courses.forEach { course ->
                val newCourse = course.copy(
                    id = 0, // 重置ID，让数据库自动生成
                    scheduleId = newScheduleId
                )
                courseDao.insertCourse(newCourse)
            }
        }
        
        return newScheduleId
    }
}