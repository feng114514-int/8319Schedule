package com.example.a8319schedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 培养方案-课程设置总表数据访问对象
 */
@Dao
interface TrainingPlanAllDao {

    @Query("SELECT * FROM training_plans_all WHERE scheduleId = :scheduleId ORDER BY id ASC")
    fun getPlansByScheduleId(scheduleId: Long): Flow<List<TrainingPlanAll>>

    @Query("SELECT * FROM training_plans_all WHERE scheduleId = :scheduleId ORDER BY id ASC")
    suspend fun getPlansListByScheduleId(scheduleId: Long): List<TrainingPlanAll>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<TrainingPlanAll>)

    @Query("DELETE FROM training_plans_all WHERE scheduleId = :scheduleId")
    suspend fun deletePlansByScheduleId(scheduleId: Long)

    @Query("DELETE FROM training_plans_all")
    suspend fun deleteAllPlans()
}
