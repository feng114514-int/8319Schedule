package com.example.a8319schedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 培养方案-执行计划数据访问对象
 */
@Dao
interface TrainingPlanDao {

    @Query("SELECT * FROM training_plans WHERE scheduleId = :scheduleId ORDER BY semester ASC, seq ASC")
    fun getPlansByScheduleId(scheduleId: Long): Flow<List<TrainingPlan>>

    @Query("SELECT * FROM training_plans WHERE scheduleId = :scheduleId ORDER BY semester ASC, seq ASC")
    suspend fun getPlansListByScheduleId(scheduleId: Long): List<TrainingPlan>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlans(plans: List<TrainingPlan>)

    @Query("DELETE FROM training_plans WHERE scheduleId = :scheduleId")
    suspend fun deletePlansByScheduleId(scheduleId: Long)

    @Query("DELETE FROM training_plans")
    suspend fun deleteAllPlans()
}
