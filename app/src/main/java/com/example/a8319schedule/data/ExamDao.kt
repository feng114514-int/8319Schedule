package com.example.a8319schedule.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 考试安排数据访问对象
 */
@Dao
interface ExamDao {

    @Query("SELECT * FROM exams WHERE scheduleId = :scheduleId ORDER BY examStartTimestamp ASC")
    fun getExamsByScheduleId(scheduleId: Long): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE scheduleId = :scheduleId ORDER BY examStartTimestamp ASC")
    suspend fun getExamsListByScheduleId(scheduleId: Long): List<Exam>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<Exam>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExamById(id: Long)

    @Query("DELETE FROM exams WHERE scheduleId = :scheduleId")
    suspend fun deleteExamsByScheduleId(scheduleId: Long)

    @Query("DELETE FROM exams")
    suspend fun deleteAllExams()
}
