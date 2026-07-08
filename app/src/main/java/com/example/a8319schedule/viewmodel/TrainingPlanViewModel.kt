package com.example.a8319schedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a8319schedule.data.CourseDatabase
import com.example.a8319schedule.data.TrainingPlan
import com.example.a8319schedule.data.TrainingPlanAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * 培养方案-执行计划 ViewModel
 * 按当前激活课表暴露培养方案列表
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrainingPlanViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CourseDatabase.getDatabase(application)
    private val planDao = db.trainingPlanDao()
    private val planAllDao = db.trainingPlanAllDao()

    // 复用 CourseViewModel 的激活课表 ID
    private val courseViewModel = CourseViewModel(application)

    val trainingPlans: StateFlow<List<TrainingPlan>> = courseViewModel.activeScheduleId.flatMapLatest { scheduleId ->
        planDao.getPlansByScheduleId(scheduleId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** 课程设置总表（所有培养方案），按当前激活课表暴露 */
    val allTrainingPlans: StateFlow<List<TrainingPlanAll>> = courseViewModel.activeScheduleId.flatMapLatest { scheduleId ->
        planAllDao.getPlansByScheduleId(scheduleId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
