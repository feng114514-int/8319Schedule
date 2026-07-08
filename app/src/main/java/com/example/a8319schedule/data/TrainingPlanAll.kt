package com.example.a8319schedule.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 培养方案-课程设置总表实体类
 * 由教务系统培养方案及完成情况页面（topyfamx）解析而来，与课表（scheduleId）关联
 *
 * 表格列序（id="mxh"）：
 * 课程体系 | 选课组 | 课程编号 | 课程名称 | 完成情况 | 课程性质 | 课程属性
 * | 学分 | 讲课学时 | 实践学时 | 实验学时 | 线上学时 | 总学时 | 开设学期
 */
@Entity(
    tableName = "training_plans_all",
    indices = [Index(value = ["scheduleId", "courseCode"], unique = true)]
)
data class TrainingPlanAll(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: Long,            // 关联课表
    val courseSystem: String,        // 课程体系，如 "创新创业课程 (应修 4 / 已修 1.5)"
    val courseGroup: String,         // 选课组（多为空）
    val courseCode: String,          // 课程编号
    val courseName: String,          // 课程名称
    val completionStatus: String,    // 完成情况（多为空）
    val courseNature: String,        // 课程性质：必修课 / 选修课
    val courseAttribute: String,     // 课程属性：必修 / 选修
    val credit: String,              // 学分
    val lectureHours: String,        // 讲课学时
    val practiceHours: String,       // 实践学时
    val experimentHours: String,     // 实验学时
    val onlineHours: String,         // 线上学时
    val totalHours: String,          // 总学时
    val openSemester: String         // 开设学期，如 "2"
)
