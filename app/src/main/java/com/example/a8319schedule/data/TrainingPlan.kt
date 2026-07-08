package com.example.a8319schedule.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 培养方案-执行计划实体类
 * 由教务系统培养方案页面（pyfa_query）解析而来，与课表（scheduleId）关联
 *
 * 表格列序（id="dataList"）：
 * 序号 | 开课学期 | 课程编号 | 课程名称 | 开课单位 | 学分 | 总学时 | 考核方式 | 课程性质 | 课程属性 | 是否考试
 */
@Entity(
    tableName = "training_plans",
    indices = [Index(value = ["scheduleId", "courseCode"], unique = true)]
)
data class TrainingPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: Long,          // 关联课表
    val seq: Int,                  // 序号
    val semester: String,          // 开课学期，如 "2025-2026-1"
    val courseCode: String,        // 课程编号
    val courseName: String,        // 课程名称
    val department: String,        // 开课单位
    val credit: String,            // 学分
    val totalHours: String,        // 总学时
    val assessmentType: String,    // 考核方式
    val courseNature: String,      // 课程性质
    val courseAttribute: String,   // 课程属性
    val isExam: String             // 是否考试
)
