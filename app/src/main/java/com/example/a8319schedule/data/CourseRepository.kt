package com.example.a8319schedule.data

import kotlinx.coroutines.flow.Flow

/**
 * 课程数据仓库
 */
class CourseRepository(
    private val dao: CourseDao
) {
    
    fun getCoursesByScheduleId(scheduleId: Long): Flow<List<Course>> = dao.getCoursesByScheduleId(scheduleId)
    
    val allCourses: Flow<List<Course>> = dao.getCoursesByScheduleId(1)
    
    fun getCoursesByDay(day: Int): Flow<List<Course>> = dao.getCoursesByDay(day)
    
    suspend fun getCoursesByWeekAndSchedule(weekNumber: Int, scheduleId: Long): List<Course> = 
        dao.getCoursesByWeekAndSchedule(weekNumber, scheduleId)
    
    suspend fun getCoursesByWeekDefault(weekNumber: Int): List<Course> = 
        dao.getCoursesByWeekAndSchedule(weekNumber, 1)
    
    suspend fun getCourseById(id: Long): Course? = dao.getCourseById(id)
    
    suspend fun insertCourse(course: Course): Long = dao.insertCourse(course)

    suspend fun updateCourse(course: Course) = dao.updateCourse(course)
    
    suspend fun deleteCourse(course: Course) = dao.deleteCourse(course)
    
    suspend fun deleteCourseById(id: Long) = dao.deleteCourseById(id)
    
    suspend fun deleteCoursesByScheduleId(scheduleId: Long) = dao.deleteCoursesByScheduleId(scheduleId)
    
    suspend fun deleteAllCourses() = dao.deleteAllCourses()
    
    suspend fun getCoursesByWeek(weekNumber: Int): List<Course> = dao.getCoursesByWeek(weekNumber)
    
    suspend fun getCoursesByGroupId(courseGroupId: String): List<Course> = dao.getCoursesByGroupId(courseGroupId)
    
    suspend fun getCourseByInstanceId(courseInstanceId: String): Course? = dao.getCourseByInstanceId(courseInstanceId)
    
    suspend fun getSimilarCourses(
        name: String,
        teacher: String,
        classroom: String,
        dayOfWeek: Int,
        startPeriod: Int,
        endPeriod: Int
    ): List<Course> = dao.getSimilarCourses(name, teacher, classroom, dayOfWeek, startPeriod, endPeriod)

    // 批量操作
    suspend fun insertCourses(courses: List<Course>): List<Long> = dao.insertCourses(courses)
    
    suspend fun updateCourses(courses: List<Course>) = dao.updateCourses(courses)
    
    suspend fun deleteCourses(courses: List<Course>) = dao.deleteCourses(courses)

    /**
     * 原子操作：删除指定课表所有课程后批量插入新课程
     */
    suspend fun replaceCoursesForSchedule(scheduleId: Long, courses: List<Course>) {
        dao.replaceCoursesForSchedule(scheduleId, courses.map { it.copy(scheduleId = scheduleId) })
    }
}
