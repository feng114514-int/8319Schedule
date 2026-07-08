package com.example.a8319schedule.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 考试安排实体类
 * 由教务系统考试安排页面（xsksap_list）解析而来，与课表（scheduleId）关联
 */
@Entity(
    tableName = "exams",
    indices = [Index(value = ["scheduleId", "courseCode", "examTimeRaw"], unique = true)]
)
data class Exam(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: Long,          // 关联课表
    val courseName: String,        // 课程名称
    val courseCode: String,        // 课程编号
    val sessionName: String,       // 考试场次
    val campus: String,            // 校区
    val teacher: String,           // 授课教师
    val invigilator: String,       // 监考老师
    val examTimeRaw: String,       // 原始时间串，如 "2026-06-29 10:25~12:05"
    val examStartTimestamp: Long,  // 开始时间戳（毫秒），用于排序/提醒
    val examEndTimestamp: Long,    // 结束时间戳（毫秒）
    val examRoom: String,          // 考场
    val seatNumber: String,        // 座位号
    val admissionTicket: String,   // 准考证号
    val remark: String,            // 备注
    val xnxqid: String             // 学年学期，如 "2025-2026-2"
)
