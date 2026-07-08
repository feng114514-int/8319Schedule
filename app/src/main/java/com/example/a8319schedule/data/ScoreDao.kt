package com.example.a8319schedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 成绩查询数据访问对象
 */
@Dao
interface ScoreDao {

    @Query("SELECT * FROM scores WHERE scheduleId = :scheduleId ORDER BY semester ASC, seq ASC")
    fun getScoresByScheduleId(scheduleId: Long): Flow<List<Score>>

    @Query("SELECT * FROM scores WHERE scheduleId = :scheduleId ORDER BY semester ASC, seq ASC")
    suspend fun getScoresListByScheduleId(scheduleId: Long): List<Score>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScores(scores: List<Score>)

    @Query("DELETE FROM scores WHERE scheduleId = :scheduleId")
    suspend fun deleteScoresByScheduleId(scheduleId: Long)

    @Query("DELETE FROM scores")
    suspend fun deleteAllScores()
}
