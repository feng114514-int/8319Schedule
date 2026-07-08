package com.example.a8319schedule.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 课表信息实体类
 * 用于存储多个课表的基本信息
 */
@Entity(tableName = "schedules")
data class ScheduleInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,               // 课表名称，如"2024春季学期"
    val description: String = "",    // 课表描述
    val isActive: Boolean = false,  // 是否为当前激活的课表
    val createdAt: Long = System.currentTimeMillis(), // 创建时间
    val updatedAt: Long = System.currentTimeMillis(),  // 更新时间
    val startDate: Long = 0L        // 开学日期时间戳，0表示使用默认值
)