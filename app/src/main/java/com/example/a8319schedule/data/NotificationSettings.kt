package com.example.a8319schedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 通知设置数据类
 */
data class NotificationSettings(
    val courseReminderEnabled: Boolean = false,       // 上课提醒总开关
    val reminderMinutesBefore: Int = 15,              // 提前多少分钟提醒
    val dailySummaryEnabled: Boolean = false,         // 每日课表摘要开关
    val dailySummaryHour: Int = 7,                    // 每日摘要推送时间（小时）
    val dailySummaryMinute: Int = 0                   // 每日摘要推送时间（分钟）
)

private val Context.notifDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_settings")

class NotificationSettingsManager(private val ctx: Context) {

    companion object {
        private val KEY_COURSE_REMINDER_ENABLED = booleanPreferencesKey("course_reminder_enabled")
        private val KEY_REMINDER_MINUTES_BEFORE = intPreferencesKey("reminder_minutes_before")
        private val KEY_DAILY_SUMMARY_ENABLED = booleanPreferencesKey("daily_summary_enabled")
        private val KEY_DAILY_SUMMARY_HOUR = intPreferencesKey("daily_summary_hour")
        private val KEY_DAILY_SUMMARY_MINUTE = intPreferencesKey("daily_summary_minute")
    }

    val settings: Flow<NotificationSettings> = ctx.notifDataStore.data.map { prefs ->
        NotificationSettings(
            courseReminderEnabled = prefs[KEY_COURSE_REMINDER_ENABLED] ?: false,
            reminderMinutesBefore = prefs[KEY_REMINDER_MINUTES_BEFORE] ?: 15,
            dailySummaryEnabled = prefs[KEY_DAILY_SUMMARY_ENABLED] ?: false,
            dailySummaryHour = prefs[KEY_DAILY_SUMMARY_HOUR] ?: 7,
            dailySummaryMinute = prefs[KEY_DAILY_SUMMARY_MINUTE] ?: 0
        )
    }

    suspend fun updateSettings(newSettings: NotificationSettings) {
        ctx.notifDataStore.edit { prefs ->
            prefs[KEY_COURSE_REMINDER_ENABLED] = newSettings.courseReminderEnabled
            prefs[KEY_REMINDER_MINUTES_BEFORE] = newSettings.reminderMinutesBefore
            prefs[KEY_DAILY_SUMMARY_ENABLED] = newSettings.dailySummaryEnabled
            prefs[KEY_DAILY_SUMMARY_HOUR] = newSettings.dailySummaryHour
            prefs[KEY_DAILY_SUMMARY_MINUTE] = newSettings.dailySummaryMinute
        }
    }
}
