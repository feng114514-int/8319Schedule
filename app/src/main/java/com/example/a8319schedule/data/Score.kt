package com.example.a8319schedule.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 成绩查询实体类
 * 由教务系统成绩查询页面（kscj/cjcx_list）解析而来，与课表（scheduleId）关联
 */
@Entity(
    tableName = "scores",
    indices = [Index(value = ["scheduleId", "courseCode", "semester"], unique = true)]
)
data class Score(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: Long,          // 关联课表
    val seq: Int,                  // 序号
    val semester: String,          // 开课学期，如 "2025-2026-1"
    val courseCode: String,        // 课程编号
    val courseName: String,        // 课程名称
    val score: String,             // 成绩
    val scoreFlag: String,         // 成绩标识
    val credit: String,            // 学分
    val totalHours: String,        // 总学时
    val gradePoint: String,        // 绩点
    val makeupSemester: String,    // 补重学期
    val assessmentType: String,    // 考核方式
    val examNature: String,        // 考试性质
    val courseAttribute: String,   // 课程属性
    val courseNature: String,      // 课程性质
    val courseCategory: String     // 课程类别
)
