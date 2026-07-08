package com.example.a8319schedule.data

import java.util.Calendar

/**
 * 计算强智教务系统的当前学年学期标识（xnxqid）
 *
 * 格式：起始年-结束年-学期号，如 "2025-2026-2" 表示 2025-2026 学年第 2 学期
 *
 * 推导规则（参考江西理工校历）：
 * - 9月~12月：秋季学期（第1学期）-> 当年-次年-1
 * - 2月~7月：春季学期（第2学期）-> 上年-当年-2
 * - 1月：仍是上学期期末 -> 上年-当年-1
 * - 8月：暑假，默认按下一学期 -> 当年-次年-1
 */
object ExamSemesterHelper {

    fun getCurrentXnxqid(calendar: Calendar = Calendar.getInstance()): String {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        return when (month) {
            in 9..12 -> "$year-${year + 1}-1"
            in 2..7 -> "${year - 1}-$year-2"
            1 -> "${year - 1}-$year-1"
            else -> "$year-${year + 1}-1" // 8月
        }
    }
}
