package com.example.a8319schedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 课表信息数据访问对象
 */
@Dao
interface ScheduleInfoDao {
    
    @Query("SELECT * FROM schedules ORDER BY updatedAt DESC")
    fun getAllSchedules(): Flow<List<ScheduleInfo>>
    
    @Query("SELECT * FROM schedules WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveSchedule(): ScheduleInfo?
    
    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: Long): ScheduleInfo?
    
    @Insert
    suspend fun insertSchedule(schedule: ScheduleInfo): Long
    
    @Update
    suspend fun updateSchedule(schedule: ScheduleInfo)
    
    @Delete
    suspend fun deleteSchedule(schedule: ScheduleInfo)
    
    @Query("UPDATE schedules SET isActive = 0")
    suspend fun deactivateAllSchedules()
    
    @Query("UPDATE schedules SET isActive = 1 WHERE id = :id")
    suspend fun activateSchedule(id: Long)
    
    @Query("UPDATE schedules SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateScheduleTimestamp(id: Long, timestamp: Long)
    
    @Query("UPDATE schedules SET startDate = :startDate, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateStartDate(id: Long, startDate: Long, timestamp: Long = System.currentTimeMillis())
}