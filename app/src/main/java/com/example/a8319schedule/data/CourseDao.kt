package com.example.a8319schedule.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 课程数据访问对象
 */
@Dao
interface CourseDao {
    
    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId ORDER BY dayOfWeek, startPeriod")
    fun getCoursesByScheduleId(scheduleId: Long): Flow<List<Course>>
    
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startPeriod")
    fun getAllCourses(): Flow<List<Course>>
    
    @Query("SELECT * FROM courses ORDER BY dayOfWeek, startPeriod")
    suspend fun getAllCoursesSync(): List<Course>
    
    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: Long): Course?
    
    @Query("SELECT * FROM courses WHERE dayOfWeek = :day ORDER BY startPeriod")
    fun getCoursesByDay(day: Int): Flow<List<Course>>
    
    @Query("SELECT * FROM courses WHERE scheduleId = :scheduleId AND dayOfWeek = :day ORDER BY startPeriod")
    fun getCoursesByScheduleIdAndDay(scheduleId: Long, day: Int): Flow<List<Course>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long
    
    @Update
    suspend fun updateCourse(course: Course)
    
    @Delete
    suspend fun deleteCourse(course: Course)
    
    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteCourseById(id: Long)
    
    @Query("DELETE FROM courses")
    suspend fun deleteAllCourses()
    
    @Query("SELECT * FROM courses WHERE weekNumber = :weekNumber AND scheduleId = :scheduleId")
    suspend fun getCoursesByWeekAndSchedule(weekNumber: Int, scheduleId: Long): List<Course>
    
    @Query("SELECT * FROM courses WHERE weekNumber = :weekNumber")
    suspend fun getCoursesByWeek(weekNumber: Int): List<Course>
    
    @Query("DELETE FROM courses WHERE scheduleId = :scheduleId")
    suspend fun deleteCoursesByScheduleId(scheduleId: Long)
    
    @Query("SELECT * FROM courses WHERE courseGroupId = :courseGroupId")
    suspend fun getCoursesByGroupId(courseGroupId: String): List<Course>
    
    @Query("SELECT * FROM courses WHERE courseInstanceId = :courseInstanceId")
    suspend fun getCourseByInstanceId(courseInstanceId: String): Course?
    
    @Query("""
        SELECT * FROM courses 
        WHERE name = :name 
        AND teacher = :teacher 
        AND classroom = :classroom 
        AND dayOfWeek = :dayOfWeek 
        AND startPeriod = :startPeriod 
        AND endPeriod = :endPeriod
    """)
    suspend fun getSimilarCourses(
        name: String,
        teacher: String,
        classroom: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int
    ): List<Course>

    // 批量操作 - 使用 @Transaction 确保原子性
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<Course>): List<Long>
    
    @Update
    @Transaction
    suspend fun updateCourses(courses: List<Course>)
    
    @Delete
    @Transaction
    suspend fun deleteCourses(courses: List<Course>)

    /**
     * 原子操作：删除指定课表所有课程后批量插入新课程
     */
    @Transaction
    suspend fun replaceCoursesForSchedule(scheduleId: Long, courses: List<Course>) {
        deleteCoursesByScheduleId(scheduleId)
        insertCourses(courses)
    }
}
