package com.example.a8319schedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

/**
 * 课程时间设置
 */
data class PeriodTime(
    val period: Int,
    val startTime: String,
    val endTime: String
)

/**
 * 学期设置
 */
data class SemesterSettings(
    val startDate: Long,
    val endDate: Long,
    val periodTimes: List<PeriodTime>
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ScheduleSettingsManager(private val ctx: Context) {
    
    companion object {
        private val START_KEY = longPreferencesKey("start_date")
        private val END_KEY = longPreferencesKey("end_date")

        fun startTimeKey(p: Int) = stringPreferencesKey("period_${p}_start")
        fun endTimeKey(p: Int) = stringPreferencesKey("period_${p}_end")
        
        val DEFAULT_TIMES = listOf(
            PeriodTime(1, "08:30", "10:05"),
            PeriodTime(2, "10:25", "12:00"),
            PeriodTime(3, "14:00", "15:35"),
            PeriodTime(4, "15:55", "17:30"),
            PeriodTime(5, "19:00", "20:35")
        )
    }
    
    val settings: Flow<SemesterSettings> = ctx.dataStore.data.map { prefs ->
        val start = prefs[START_KEY] ?: getDefaultStart()
        val end = prefs[END_KEY] ?: getDefaultEnd()
        val times = getTimes(prefs)
        SemesterSettings(start, end, times)
    }

    suspend fun saveDates(start: Long) {
        ctx.dataStore.edit { prefs ->
            prefs[START_KEY] = start
            val calendar = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.WEEK_OF_YEAR, 20)
            }
            prefs[END_KEY] = calendar.timeInMillis
        }
    }
    
    suspend fun saveDates(start: Long, end: Long) {
        ctx.dataStore.edit { prefs ->
            prefs[START_KEY] = start
            prefs[END_KEY] = end
        }
    }
    
    suspend fun savePeriodTime(p: Int, start: String, end: String) {
        ctx.dataStore.edit { prefs ->
            prefs[startTimeKey(p)] = start
            prefs[endTimeKey(p)] = end
        }
    }
    
    suspend fun saveAllPeriodTimes(times: List<PeriodTime>) {
        ctx.dataStore.edit { prefs ->
            times.forEach { t ->
                prefs[startTimeKey(t.period)] = t.startTime
                prefs[endTimeKey(t.period)] = t.endTime
            }
        }
    }

    suspend fun saveStartDate(year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        saveDates(cal.timeInMillis)
    }
    
    private fun getTimes(prefs: Preferences): List<PeriodTime> {
        return (1..5).map { p ->
            val start = prefs[startTimeKey(p)] ?: DEFAULT_TIMES[p - 1].startTime
            val end = prefs[endTimeKey(p)] ?: DEFAULT_TIMES[p - 1].endTime
            PeriodTime(p, start, end)
        }
    }
    
    private fun getDefaultStart(): Long {
        return Calendar.getInstance().timeInMillis
    }
    
    private fun getDefaultEnd(): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.WEEK_OF_YEAR, 16)
        return cal.timeInMillis
    }
    
    // 使用统一的 WeekCalculator 计算当前周次
    fun getCurrentWeek(settings: SemesterSettings): Int {
        return WeekCalculator.calculateCurrentWeekForSettings(settings)
    }
}
