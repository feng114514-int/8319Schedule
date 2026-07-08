package com.example.a8319schedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a8319schedule.data.CourseDatabase
import com.example.a8319schedule.data.Exam
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 考试安排 ViewModel
 * 按当前激活课表暴露考试列表
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExamViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CourseDatabase.getDatabase(application)
    private val examDao = db.examDao()

    // 复用 CourseViewModel 的激活课表 ID
    private val courseViewModel = CourseViewModel(application)

    val exams: StateFlow<List<Exam>> = courseViewModel.activeScheduleId.flatMapLatest { scheduleId ->
        examDao.getExamsByScheduleId(scheduleId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** 当前激活课表 ID，供添加考试时使用 */
    val activeScheduleId: StateFlow<Long> get() = courseViewModel.activeScheduleId

    /**
     * 添加考试（自动绑定当前激活课表）
     */
    fun addExam(exam: Exam) {
        viewModelScope.launch {
            examDao.insertExam(exam.copy(scheduleId = courseViewModel.activeScheduleId.value))
        }
    }

    /**
     * 更新考试（保留原 id 和 scheduleId）
     */
    fun updateExam(exam: Exam) {
        viewModelScope.launch {
            examDao.insertExam(exam)
        }
    }

    /**
     * 删除指定考试
     */
    fun deleteExam(id: Long) {
        viewModelScope.launch {
            examDao.deleteExamById(id)
        }
    }
}
