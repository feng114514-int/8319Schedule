package com.example.a8319schedule.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a8319schedule.data.CourseDatabase
import com.example.a8319schedule.data.Score
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * 成绩查询 ViewModel
 * 按当前激活课表暴露成绩列表
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScoreViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CourseDatabase.getDatabase(application)
    private val scoreDao = db.scoreDao()

    // 复用 CourseViewModel 的激活课表 ID
    private val courseViewModel = CourseViewModel(application)

    val scores: StateFlow<List<Score>> = courseViewModel.activeScheduleId.flatMapLatest { scheduleId ->
        scoreDao.getScoresByScheduleId(scheduleId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}
