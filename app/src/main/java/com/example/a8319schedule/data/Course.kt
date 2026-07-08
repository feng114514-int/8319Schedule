package com.example.a8319schedule.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 课程实体类
 * 每个课程实例代表一个独立的课时（单周）
 */
@Entity(tableName = "courses")
data class Course(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val teacher: String = "",
    val classroom: String = "",
    val dayOfWeek: Int,
    val weekNumber: Int,  // 周次（1-20）
    val startPeriod: Int,
    val endPeriod: Int,
    val color: Long = 0xFF4CAF50L,
    // 课程组ID，用于标识同一门课程的不同周次实例（同一门课程的所有周次有相同的groupId）
    val courseGroupId: String = "",
    // 课程实例ID，用于唯一标识每个课程实例
    val courseInstanceId: String = "",
    // 所属课表ID，用于关联到具体的课表
    val scheduleId: Long = 1L  // 默认课表ID为1
)
