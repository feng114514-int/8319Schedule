package com.example.a8319schedule.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.data.CourseDatabase
import com.example.a8319schedule.data.CourseRepository
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.data.ScheduleRepository
import com.example.a8319schedule.data.TimetableParser
import com.example.a8319schedule.data.WeekCalculator
import com.example.a8319schedule.ScheduleWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CourseViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "CourseViewModel"
    }
    
    private lateinit var repo: CourseRepository
    private lateinit var scheduleRepo: ScheduleRepository
    private val settings = com.example.a8319schedule.data.ScheduleSettingsManager(application)
    
    private val _activeScheduleId = MutableStateFlow(1L)
    val activeScheduleId: StateFlow<Long> = _activeScheduleId.asStateFlow()
    
    private val _activeSchedule = MutableStateFlow<ScheduleInfo?>(null)
    val activeSchedule: StateFlow<ScheduleInfo?> = _activeSchedule.asStateFlow()
    
    val allCourses: StateFlow<List<Course>> = _activeScheduleId.flatMapLatest { scheduleId ->
        repo.getCoursesByScheduleId(scheduleId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    private val _currentWeek = MutableStateFlow(1)
    val currentWeek: StateFlow<Int> = _currentWeek.asStateFlow()
    
    init {
        val db = CourseDatabase.getDatabase(application)
        repo = CourseRepository(db.courseDao())
        scheduleRepo = ScheduleRepository(db.scheduleInfoDao(), db.courseDao())
        
        viewModelScope.launch {
            try {
                val activeSchedule = scheduleRepo.getActiveSchedule()
                _activeScheduleId.value = activeSchedule?.id ?: 1L
                _activeSchedule.value = activeSchedule
            } catch (e: Exception) {
                _activeScheduleId.value = 1L
            }
        }
        
        viewModelScope.launch {
            _activeScheduleId.collect { scheduleId ->
                viewModelScope.launch {
                    try {
                        val activeSchedule = scheduleRepo.getScheduleById(scheduleId)
                        _activeSchedule.value = activeSchedule
                        _currentWeek.value = WeekCalculator.calculateCurrentWeekForSchedule(activeSchedule)
                    } catch (e: Exception) {
                        _currentWeek.value = WeekCalculator.calculateCurrentWeek(WeekCalculator.getDefaultStartDate())
                    }
                }
            }
        }
    }
    
    fun setCurrentWeek(week: Int) {
        _currentWeek.value = week
    }
    
    fun setActiveScheduleId(scheduleId: Long) {
        _activeScheduleId.value = scheduleId
    }
    
    fun addCourse(course: Course) {
        viewModelScope.launch {
            repo.insertCourse(course)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    fun addCourseWithId(course: Course) {
        viewModelScope.launch {
            repo.insertCourse(course)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    fun updateCourse(course: Course) {
        viewModelScope.launch {
            repo.updateCourse(course)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    fun deleteCourse(course: Course) {
        viewModelScope.launch {
            repo.deleteCourse(course)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    fun deleteCourseById(courseId: Long) {
        viewModelScope.launch {
            repo.deleteCourseById(courseId)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    fun deleteCoursesByScheduleId(scheduleId: Long) {
        viewModelScope.launch {
            repo.deleteCoursesByScheduleId(scheduleId)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }

    /**
     * 替换指定课表的所有课程（原子操作，先删后插）
     */
    suspend fun replaceCoursesForSchedule(scheduleId: Long, courses: List<Course>) {
        repo.replaceCoursesForSchedule(scheduleId, courses)
        ScheduleWidgetProvider.updateAllWidgets(getApplication())
    }
    
    suspend fun getCourseById(courseId: Long): Course? {
        return repo.getCourseById(courseId)
    }
    
    suspend fun getCoursesByWeek(weekNumber: Int): List<Course> {
        return repo.getCoursesByWeek(weekNumber)
    }
    
    suspend fun getCoursesByWeekRangeAndSchedule(startWeek: Int, endWeek: Int, scheduleId: Long): List<Course> {
        val allCourses = mutableListOf<Course>()
        for (week in startWeek..endWeek) {
            val weekCourses = repo.getCoursesByWeekAndSchedule(week, scheduleId)
            allCourses.addAll(weekCourses)
        }
        return allCourses
    }
    
    suspend fun getCourseInWeek(courseGroupId: String, weekNumber: Int): Course? {
        val coursesInWeek = getCoursesByWeek(weekNumber)
        return coursesInWeek.find { course ->
            course.courseGroupId == courseGroupId && 
            course.weekNumber == weekNumber
        }
    }
    
    suspend fun getCoursesByGroupId(courseGroupId: String): List<Course> {
        return repo.getCoursesByGroupId(courseGroupId)
    }
    
    suspend fun getCourseByInstanceId(courseInstanceId: String): Course? {
        return repo.getCourseByInstanceId(courseInstanceId)
    }
    
    suspend fun getSimilarCourses(course: Course): List<Course> {
        return repo.getSimilarCourses(
            name = course.name,
            teacher = course.teacher,
            classroom = course.classroom,
            dayOfWeek = course.dayOfWeek,
            startPeriod = course.startPeriod,
            endPeriod = course.endPeriod
        )
    }
    
    // 批量更新课程 - 使用 Room @Transaction 批量操作
    fun updateCourses(courses: List<Course>) {
        viewModelScope.launch {
            repo.updateCourses(courses)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    // 批量删除课程 - 使用 Room @Transaction 批量操作
    fun deleteCourses(courses: List<Course>) {
        viewModelScope.launch {
            repo.deleteCourses(courses)
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    fun deleteCoursesInWeek(weekNumber: Int, course: Course) {
        viewModelScope.launch {
            val courses = repo.getCoursesByWeek(weekNumber)
            val matchingCourses = courses.filter {
                it.name == course.name && 
                it.teacher == course.teacher &&
                it.classroom == course.classroom &&
                it.dayOfWeek == course.dayOfWeek
            }
            if (matchingCourses.isNotEmpty()) {
                repo.deleteCourses(matchingCourses)
            }
            ScheduleWidgetProvider.updateAllWidgets(getApplication())
        }
    }
    
    fun generateMissingCourseGroupIds() {
        viewModelScope.launch {
            try {
                val allCoursesList = allCourses.value
                val coursesWithoutGroupId = allCoursesList.filter { it.courseGroupId.isEmpty() }
                
                val updatedCourses = coursesWithoutGroupId.map { course ->
                    course.copy(
                        courseGroupId = "${course.name}_${course.teacher}_${course.classroom}_${course.startPeriod}"
                    )
                }
                
                if (updatedCourses.isNotEmpty()) {
                    repo.updateCourses(updatedCourses)
                    ScheduleWidgetProvider.updateAllWidgets(getApplication())
                }
            } catch (e: Exception) {
                Log.e(TAG, "生成courseGroupId时出错: ${e.message}", e)
            }
        }
    }
    
    suspend fun parsePlainText(text: String): TimetableParser.ParseResult {
        return try {
            TimetableParser.parsePlainText(text)
        } catch (e: Exception) {
            TimetableParser.ParseResult(
                courses = emptyList(),
                success = false,
                message = "解析失败: ${e.message}"
            )
        }
    }
    
    // 修复：只删除当前激活课表的课程，而不是所有课表的课程
    fun importFromPlainText(text: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = TimetableParser.parsePlainText(text)
                
                if (result.success) {
                    // 只删除当前激活课表的现有课程
                    val currentScheduleId = _activeScheduleId.value
                    repo.deleteCoursesByScheduleId(currentScheduleId)
                    
                    // 插入新课程（设置正确的scheduleId）
                    val coursesWithScheduleId = result.courses.map { course ->
                        course.copy(scheduleId = currentScheduleId)
                    }
                    repo.insertCourses(coursesWithScheduleId)
                    
                    ScheduleWidgetProvider.updateAllWidgets(getApplication())
                    
                    onResult("成功导入 ${result.courses.size} 门课程")
                } else {
                    onResult("导入失败: ${result.message}")
                }
            } catch (e: Exception) {
                onResult("导入失败: ${e.message}")
            }
        }
    }
}
