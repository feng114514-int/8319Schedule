package com.example.a8319schedule.data

import android.content.Context
import android.util.Log

/**
 * 其它信息（考试/成绩/培养方案）统一入库工具。
 *
 * 解析 HTML → 按 scheduleId 清空旧数据 → 插入新数据。
 * 单项失败不影响其它项；返回导入条数，失败抛异常或返回 0。
 *
 * 所有方法均为 suspend，需在 IO 调度器中调用。
 */
object OtherInfoImporter {

    private const val TAG = "OtherInfoImporter"

    /** 导入考试安排，返回导入条数（0 表示无数据或失败）。 */
    suspend fun importExams(context: Context, html: String, scheduleId: Long, xnxqid: String): Int {
        return try {
            val result = ExamParser.parse(html, scheduleId, xnxqid)
            if (result.exams.isEmpty()) {
                Log.w(TAG, "考试安排解析为空: ${result.message}")
                0
            } else {
                val dao = CourseDatabase.getDatabase(context).examDao()
                dao.deleteExamsByScheduleId(scheduleId)
                dao.insertExams(result.exams)
                result.exams.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "考试安排导入失败", e)
            0
        }
    }

    /** 导入成绩查询，返回导入条数。 */
    suspend fun importScores(context: Context, html: String, scheduleId: Long): Int {
        return try {
            val result = ScoreParser.parse(html, scheduleId)
            if (result.scores.isEmpty()) {
                Log.w(TAG, "成绩解析为空: ${result.message}")
                0
            } else {
                val dao = CourseDatabase.getDatabase(context).scoreDao()
                dao.deleteScoresByScheduleId(scheduleId)
                dao.insertScores(result.scores)
                result.scores.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "成绩导入失败", e)
            0
        }
    }

    /** 导入培养方案（执行计划），返回导入条数。 */
    suspend fun importTrainingPlans(context: Context, html: String, scheduleId: Long): Int {
        return try {
            val result = TrainingPlanParser.parse(html, scheduleId)
            if (result.plans.isEmpty()) {
                Log.w(TAG, "培养方案解析为空: ${result.message}")
                0
            } else {
                val dao = CourseDatabase.getDatabase(context).trainingPlanDao()
                dao.deletePlansByScheduleId(scheduleId)
                dao.insertPlans(result.plans)
                result.plans.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "培养方案导入失败", e)
            0
        }
    }

    /** 导入培养方案（课程设置总表），返回导入条数。 */
    suspend fun importTrainingPlansAll(context: Context, html: String, scheduleId: Long): Int {
        return try {
            val result = TrainingPlanAllParser.parse(html, scheduleId)
            if (result.plans.isEmpty()) {
                Log.w(TAG, "课程设置总表解析为空: ${result.message}")
                0
            } else {
                val dao = CourseDatabase.getDatabase(context).trainingPlanAllDao()
                dao.deletePlansByScheduleId(scheduleId)
                dao.insertPlans(result.plans)
                result.plans.size
            }
        } catch (e: Exception) {
            Log.e(TAG, "课程设置总表导入失败", e)
            0
        }
    }
}
