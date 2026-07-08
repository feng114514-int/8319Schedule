package com.example.a8319schedule.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.data.CourseScheduleTimes
import com.example.a8319schedule.data.PeriodTime
import com.example.a8319schedule.data.WeekCalculator
import com.example.a8319schedule.viewmodel.CourseViewModel
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import top.yukonga.miuix.kmp.basic.*

@Composable
fun ExportScreen(
    viewModel: CourseViewModel = viewModel(),
    settingsManager: com.example.a8319schedule.data.ScheduleSettingsManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val semesterSettings by settingsManager.settings.collectAsState(initial = com.example.a8319schedule.data.SemesterSettings(0, 0, emptyList()))
    val activeSchedule by viewModel.activeSchedule.collectAsState()
    
    var reminderMinutes by remember { mutableStateOf(15) }
    var startWeek by remember { mutableStateOf(1) }
    var endWeek by remember { mutableStateOf(20) }
    var exportAllWeeks by remember { mutableStateOf(true) }
    var calendarName by remember { mutableStateOf("课程表") }
    
    val allCourses by viewModel.allCourses.collectAsState()
    
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/calendar"),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    try {
                        // Bug修复：使用ScheduleInfo.startDate（数据库）而非SemesterSettings.startDate（DataStore）
                        // 课表页面/小部件都使用ScheduleInfo.startDate，导出也必须一致
                        val startDateMillis = if (activeSchedule != null && activeSchedule!!.startDate > 0) {
                            activeSchedule!!.startDate
                        } else {
                            WeekCalculator.getDefaultStartDate()
                        }
                        
                        val icsContent = generateIcsContent(
                            courses = allCourses,
                            startDateMillis = startDateMillis,
                            periodTimes = semesterSettings.periodTimes,
                            reminderMinutes = reminderMinutes,
                            startWeek = if (exportAllWeeks) null else startWeek,
                            endWeek = if (exportAllWeeks) null else endWeek,
                            calendarName = calendarName
                        )
                        
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(icsContent.toByteArray())
                            outputStream.flush()
                        }
                        
                        Toast.makeText(context, "导出成功！", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    )

    Scaffold(
        topBar = {
            HyperOSScreenTopBar(
                title = "导出课表",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "导出格式",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("日历格式 (.ics)", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = "可导入到手机日历、Google Calendar、Outlook等",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            RadioButton(
                                selected = true,
                                onClick = { }
                            )
                        }
                    }
                }
            }
            
            item {
                HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "日历设置",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        TextField(
                            value = calendarName,
                            onValueChange = { calendarName = it },
                            label = "日历名称",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            cornerRadius = 12.dp
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("提醒时间", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val reminderOptions = listOf(
                            "不提醒" to 0,
                            "上课前5分钟" to 5,
                            "上课前10分钟" to 10,
                            "上课前15分钟" to 15,
                            "上课前30分钟" to 30
                        )
                        
                        reminderOptions.forEach { (label, minutes) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { reminderMinutes = minutes }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = reminderMinutes == minutes,
                                    onClick = { reminderMinutes = minutes }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            
            item {
                HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "周次范围",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { exportAllWeeks = true }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = exportAllWeeks,
                                onClick = { exportAllWeeks = true }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("导出所有周次", fontSize = 14.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { exportAllWeeks = false }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !exportAllWeeks,
                                onClick = { exportAllWeeks = false }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("指定周次范围", fontSize = 14.sp)
                        }
                        
                        if (!exportAllWeeks) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("从", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = startWeek.toString(),
                                    onValueChange = { 
                                        startWeek = it.toIntOrNull() ?: 1
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    cornerRadius = 8.dp
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("至", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                TextField(
                                    value = endWeek.toString(),
                                    onValueChange = { 
                                        endWeek = it.toIntOrNull() ?: 20
                                    },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    cornerRadius = 8.dp
                                )
                            }
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        val fileName = "$calendarName.ics"
                        fileLauncher.launch(fileName)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    cornerRadius = 12.dp
                ) {
                    Text(
                        text = "导出课表",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "共 ${allCourses.size} 门课程",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun generateIcsContent(
    courses: List<com.example.a8319schedule.data.Course>,
    startDateMillis: Long,
    periodTimes: List<PeriodTime>,
    reminderMinutes: Int = 15,
    startWeek: Int? = null,
    endWeek: Int? = null,
    calendarName: String = "课程表"
): String {
    val fullSchedule = CourseScheduleTimes.fullSchedule
    
    val weekdayOffset = mapOf(
        1 to 0, 2 to 1, 3 to 2, 4 to 3, 5 to 4, 6 to 5, 7 to 6
    )
    
    val now = Date()
    val dtstamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(now)
    
    val weeksToExport = if (startWeek == null || endWeek == null) {
        courses.map { it.weekNumber }.distinct().sorted()
    } else {
        courses.map { it.weekNumber }.distinct().filter { it in startWeek..endWeek }.sorted()
    }
    
    // Bug修复：分组key加入endPeriod，避免不同节数的课程被错误合并
    val courseGroups = courses
        .filter { it.weekNumber in weeksToExport }
        .groupBy { course ->
            Triple(course.courseGroupId, course.dayOfWeek, course.startPeriod to course.endPeriod)
        }
    
    val icsLines = mutableListOf<String>()
    
    icsLines.add("BEGIN:VCALENDAR")
    icsLines.add("VERSION:2.0")
    icsLines.add("PRODID:-//8319Schedule//CN")
    icsLines.add("NAME:$calendarName")
    icsLines.add("X-WR-CALNAME:$calendarName")
    
    icsLines.add("BEGIN:VTIMEZONE")
    icsLines.add("TZID:Asia/Shanghai")
    icsLines.add("X-WR-TIMEZONE:Asia/Shanghai")
    icsLines.add("BEGIN:STANDARD")
    icsLines.add("TZOFFSETFROM:+0800")
    icsLines.add("TZOFFSETTO:+0800")
    icsLines.add("TZNAME:CST")
    icsLines.add("DTSTART:19700101T000000")
    icsLines.add("END:STANDARD")
    icsLines.add("END:VTIMEZONE")
    
    for ((key, courseList) in courseGroups) {
        val sampleCourse = courseList.first()
        val weeks = courseList.map { it.weekNumber }.sorted()
        val weekRanges = mergeConsecutiveWeeks(weeks)
        
        // Bug修复：获取课程时间时优先使用用户自定义的periodTimes，再回退到fullSchedule
        val timeRange = getCourseTimeRange(sampleCourse.startPeriod, sampleCourse.endPeriod, periodTimes, fullSchedule)
        if (timeRange == null) continue
        
        val (startTime, endTime) = timeRange
        
        for ((startW, endW) in weekRanges) {
            val daysOffset = (startW - 1) * 7 + (weekdayOffset[sampleCourse.dayOfWeek] ?: 0)
            val startDate = Calendar.getInstance().apply {
                timeInMillis = startDateMillis
                while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                add(Calendar.DAY_OF_YEAR, daysOffset)
            }
            
            val startCal = Calendar.getInstance().apply {
                time = startDate.time
                set(Calendar.HOUR_OF_DAY, startTime.first)
                set(Calendar.MINUTE, startTime.second)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val endCal = Calendar.getInstance().apply {
                time = startDate.time
                set(Calendar.HOUR_OF_DAY, endTime.first)
                set(Calendar.MINUTE, endTime.second)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val lastDaysOffset = (endW - 1) * 7 + (weekdayOffset[sampleCourse.dayOfWeek] ?: 0)
            val lastDateCal = Calendar.getInstance().apply {
                timeInMillis = startDateMillis
                while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                    add(Calendar.DAY_OF_MONTH, -1)
                }
                add(Calendar.DAY_OF_YEAR, lastDaysOffset)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }
            
            icsLines.add("BEGIN:VEVENT")
            icsLines.add("DTSTAMP:$dtstamp")
            icsLines.add("UID:8319Schedule-${sampleCourse.courseGroupId}-${sampleCourse.startPeriod}-${sampleCourse.endPeriod}-$startW-$endW")
            icsLines.add("SUMMARY:${sampleCourse.name}")
            icsLines.add("DTSTART;TZID=Asia/Shanghai:${formatDateTime(startCal)}")
            icsLines.add("DTEND;TZID=Asia/Shanghai:${formatDateTime(endCal)}")
            icsLines.add("RRULE:FREQ=WEEKLY;UNTIL=${formatDateTimeUtc(lastDateCal)};INTERVAL=1;BYDAY=${getDayOfWeekString(sampleCourse.dayOfWeek)}")
            
            val location = if (sampleCourse.teacher.isNotEmpty() && sampleCourse.classroom.isNotEmpty()) {
                "${sampleCourse.classroom} ${sampleCourse.teacher}"
            } else {
                sampleCourse.classroom
            }
            
            if (location.isNotEmpty()) {
                icsLines.add("LOCATION:$location")
            }
            
            val description = "第${sampleCourse.startPeriod}-${sampleCourse.endPeriod}节\\n${sampleCourse.classroom}\\n${sampleCourse.teacher}"
            icsLines.add("DESCRIPTION:$description")
            
            if (reminderMinutes > 0) {
                icsLines.add("BEGIN:VALARM")
                icsLines.add("ACTION:DISPLAY")
                icsLines.add("DESCRIPTION:${sampleCourse.name}")
                icsLines.add("TRIGGER:-PT${reminderMinutes}M")
                icsLines.add("END:VALARM")
            }
            
            icsLines.add("END:VEVENT")
        }
    }
    
    icsLines.add("END:VCALENDAR")
    
    return icsLines.joinToString("\r\n")
}

/**
 * 获取课程的开始和结束时间
 * 优先使用用户自定义的periodTimes（大节），再回退到fullSchedule（小节默认时间）
 * 修复了fullSchedule只支持1-10节导致>10节课程消失的Bug
 * 
 * @return Pair<(startHour, startMinute), (endHour, endMinute)> 或 null（无法确定时间时）
 */
private fun getCourseTimeRange(
    startPeriod: Int,
    endPeriod: Int,
    periodTimes: List<PeriodTime>,
    fullSchedule: Map<Int, CourseScheduleTimes.PeriodTimeSlot>
): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
    // 策略1：使用用户自定义的periodTimes（大节1-5）
    // 小节 → 大节映射：小节1-2 → 大节1，小节3-4 → 大节2，...
    val startBigPeriod = (startPeriod - 1) / 2 + 1
    val endBigPeriod = (endPeriod - 1) / 2 + 1
    
    val startPt = periodTimes.find { it.period == startBigPeriod }
    val endPt = periodTimes.find { it.period == endBigPeriod }
    
    if (startPt != null && endPt != null) {
        val startTime = parseTimeString(startPt.startTime)
        val endTime = parseTimeString(endPt.endTime)
        if (startTime != null && endTime != null) {
            return startTime to endTime
        }
    }
    
    // 策略2：回退到fullSchedule（默认的小节时间表）
    val startSlot = fullSchedule[startPeriod]
    val endSlot = fullSchedule[endPeriod]
    if (startSlot != null && endSlot != null) {
        return (startSlot.startHour to startSlot.startMinute) to (endSlot.endHour to endSlot.endMinute)
    }
    
    // 策略3：如果endPeriod超过fullSchedule范围，尝试用最后一个大节的结束时间
    // 例如：startPeriod=9, endPeriod=12 → 大节5的起始时间 ~ 最后已知时间的延伸
    if (startSlot != null && endSlot == null && endPeriod > fullSchedule.keys.maxOrNull()!!) {
        // 用startPeriod的开始时间 + 根据大节推算的结束时间
        val lastBigPeriod = periodTimes.lastOrNull()
        if (lastBigPeriod != null) {
            val endBigPt = periodTimes.find { it.period == endBigPeriod }
            if (endBigPt != null) {
                val endTime = parseTimeString(endBigPt.endTime)
                if (endTime != null) {
                    return (startSlot.startHour to startSlot.startMinute) to endTime
                }
            }
        }
        // 最后手段：用fullSchedule最后一个slot的结束时间 + 差值估算
        val lastSlot = fullSchedule[fullSchedule.keys.maxOrNull()!!]!!
        val extraSmallPeriods = endPeriod - fullSchedule.keys.maxOrNull()!!
        // 每个小节约45分钟
        val extraMinutes = extraSmallPeriods * 45
        val lastEndMinuteOfDay = lastSlot.endHour * 60 + lastSlot.endMinute + extraMinutes
        return (startSlot.startHour to startSlot.startMinute) to 
               (lastEndMinuteOfDay / 60 to lastEndMinuteOfDay % 60)
    }
    
    return null
}

/**
 * 解析时间字符串 "HH:mm" 为 (hour, minute)
 */
private fun parseTimeString(time: String): Pair<Int, Int>? {
    val parts = time.split(":")
    if (parts.size == 2) {
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }
    return null
}

fun mergeConsecutiveWeeks(weeks: List<Int>): List<Pair<Int, Int>> {
    if (weeks.isEmpty()) return emptyList()
    
    val sortedWeeks = weeks.distinct().sorted()
    val ranges = mutableListOf<Pair<Int, Int>>()
    
    var start = sortedWeeks[0]
    var end = sortedWeeks[0]
    
    for (i in 1 until sortedWeeks.size) {
        if (sortedWeeks[i] == end + 1) {
            end = sortedWeeks[i]
        } else {
            ranges.add(start to end)
            start = sortedWeeks[i]
            end = sortedWeeks[i]
        }
    }
    
    ranges.add(start to end)
    return ranges
}

fun formatDateTime(calendar: Calendar): String {
    val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    return sdf.format(calendar.time)
}

fun formatDateTimeUtc(calendar: Calendar): String {
    val sdf = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault())
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(calendar.time)
}

fun getDayOfWeekString(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "MO"
        2 -> "TU"
        3 -> "WE"
        4 -> "TH"
        5 -> "FR"
        6 -> "SA"
        7 -> "SU"
        else -> "MO"
    }
}
