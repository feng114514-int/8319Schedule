package com.example.a8319schedule.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.data.OtherInfoImporter
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.data.TimetableParser
import com.example.a8319schedule.data.ScheduleSettingsManager
import com.example.a8319schedule.viewmodel.CourseViewModel
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast as AndroidToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleImportSettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    settingsManager: ScheduleSettingsManager,
    parseResult: TimetableParser.ParseResult,
    scheduleId: Long,
    scheduleName: String,
    isNewSchedule: Boolean,
    examHtml: String? = null,
    examXnxqid: String = "",
    planHtml: String? = null,
    allPlanHtml: String? = null,
    scoreHtml: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val schedules by scheduleViewModel.schedules.collectAsState()
    val activeSchedule by scheduleViewModel.activeSchedule.collectAsState()
    
    var scheduleDescription by remember { mutableStateOf("") }
    var finalScheduleId by remember { mutableStateOf(scheduleId) }
    var finalScheduleName by remember { mutableStateOf(scheduleName) }
    var finalIsNewSchedule by remember { mutableStateOf(isNewSchedule) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedScheduleOption by remember { mutableStateOf(if (isNewSchedule) "new" else "existing") }
    
    val calendar = Calendar.getInstance()
    val currentYear = calendar.get(Calendar.YEAR)
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    
    var startYear by remember { mutableStateOf(currentYear) }
    var startMonth by remember { mutableStateOf(currentMonth) }
    var startDay by remember { mutableStateOf(currentDay) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // HyperOS 风格配色
    val hyperBackground = Color(0xFFF5F5F5)
    val hyperPrimary = Color(0xFF1A73E8)
    val hyperSurface = Color.White
    val hyperOnSurface = Color(0xFF1F1F1F)
    val hyperOnSurfaceVariant = Color(0xFF757575)
    val hyperPrimaryContainer = Color(0xFFE8F0FE)
    
    val dateFormatter = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
    val startDateString = remember(startYear, startMonth, startDay) {
        val tempCalendar = Calendar.getInstance()
        tempCalendar.set(startYear, startMonth, startDay)
        dateFormatter.format(tempCalendar.time)
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(hyperBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部导航栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = hyperSurface,
                tonalElevation = 0.dp,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = hyperOnSurface
                        )
                    }
                    Text(
                        text = "课表设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = hyperOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 课表基本信息卡片 - HyperOS 风格
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = hyperSurface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "导入设置",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = hyperOnSurface
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 导入方式选择
                        Text(
                            text = "导入方式",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = hyperOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 覆盖现有课表选项
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { 
                                        selectedScheduleOption = "existing"
                                        finalIsNewSchedule = false
                                    },
                                color = if (selectedScheduleOption == "existing") hyperPrimaryContainer else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedScheduleOption == "existing") {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选择",
                                            tint = hyperPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "覆盖现有",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedScheduleOption == "existing") hyperPrimary else hyperOnSurface
                                    )
                                }
                            }
                            
                            // 创建新课表选项
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { 
                                        selectedScheduleOption = "new"
                                        finalIsNewSchedule = true
                                    },
                                color = if (selectedScheduleOption == "new") hyperPrimaryContainer else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectedScheduleOption == "new") {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "已选择",
                                            tint = hyperPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "创建新课表",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (selectedScheduleOption == "new") hyperPrimary else hyperOnSurface
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 根据选择显示不同的输入框
                        if (selectedScheduleOption == "existing") {
                            if (schedules.isNotEmpty()) {
                                Text(
                                    text = "选择要覆盖的课表",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = hyperOnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                schedules.forEach { schedule ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { 
                                                finalScheduleId = schedule.id
                                                finalScheduleName = schedule.name
                                            },
                                        color = if (finalScheduleId == schedule.id) hyperPrimaryContainer else Color(0xFFF8F8F8),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = if (finalScheduleId == schedule.id) hyperPrimary else hyperOnSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = schedule.name,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = hyperOnSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (schedule.isActive) {
                                                Text(
                                                    text = "当前",
                                                    fontSize = 12.sp,
                                                    color = hyperPrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (finalScheduleId == schedule.id) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = "已选择",
                                                    tint = hyperPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "暂无课表，请先创建新课表",
                                    color = Color(0xFFE53935)
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = finalScheduleName,
                                onValueChange = { finalScheduleName = it },
                                label = { Text("课表名称") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hyperPrimary,
                                    focusedLabelColor = hyperPrimary,
                                    cursorColor = hyperPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = scheduleDescription,
                                onValueChange = { scheduleDescription = it },
                                label = { Text("课表描述（可选）") },
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = hyperPrimary,
                                    focusedLabelColor = hyperPrimary,
                                    cursorColor = hyperPrimary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 开学日期设置
                        Text(
                            text = "开学日期",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = hyperOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDatePicker = true },
                            color = Color(0xFFF8F8F8),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = hyperPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = startDateString,
                                    fontSize = 15.sp,
                                    modifier = Modifier.weight(1f),
                                    color = hyperOnSurface
                                )
                                Text(
                                    text = "选择",
                                    fontSize = 14.sp,
                                    color = hyperPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                
                // 课程信息卡片 - HyperOS 风格
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = hyperSurface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "将导入",
                                fontSize = 16.sp,
                                color = hyperOnSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${parseResult.courses.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = hyperPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "门课程",
                                fontSize = 16.sp,
                                color = hyperOnSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "课程预览：",
                            fontSize = 14.sp,
                            color = hyperOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        parseResult.courses.take(3).forEach { course ->
                            Text(
                                "• ${course.name}",
                                fontSize = 14.sp,
                                color = hyperOnSurface,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        
                        if (parseResult.courses.size > 3) {
                            Text(
                                "...还有 ${parseResult.courses.size - 3} 门课程",
                                fontSize = 13.sp,
                                color = hyperOnSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = hyperOnSurfaceVariant
                        )
                    ) {
                        Text("取消", fontWeight = FontWeight.Medium)
                    }
                    
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                try {
                                    if (finalIsNewSchedule) {
                                        if (finalScheduleName.isBlank()) {
                                            withContext(Dispatchers.Main) {
                                                AndroidToast.makeText(
                                                    context,
                                                    "请输入课表名称",
                                                    AndroidToast.LENGTH_SHORT
                                                ).show()
                                            }
                                            return@launch
                                        }
                                        
                                        scheduleViewModel.createSchedule(finalScheduleName, scheduleDescription)
                                        finalScheduleId = scheduleViewModel.activeScheduleId.value
                                    } else {
                                        if (finalScheduleId <= 0 && schedules.isNotEmpty()) {
                                            finalScheduleId = schedules.first().id
                                            finalScheduleName = schedules.first().name
                                        }
                                    }
                                    
                                    val calendar = Calendar.getInstance()
                                    calendar.set(startYear, startMonth, startDay, 0, 0, 0)
                                    calendar.set(Calendar.MILLISECOND, 0)
                                    scheduleViewModel.updateStartDate(finalScheduleId, calendar.timeInMillis)
                                    
                                    viewModel.deleteCoursesByScheduleId(finalScheduleId)
                                    
                                    parseResult.courses.forEach { course ->
                                        val updatedCourse = course.copy(scheduleId = finalScheduleId)
                                        viewModel.addCourse(updatedCourse)
                                    }
                                    
                                    scheduleViewModel.activateSchedule(finalScheduleId)
                                    
                                    val todayCalendar = Calendar.getInstance()
                                    val today = todayCalendar.timeInMillis
                                    val startCalendar = Calendar.getInstance().apply {
                                        set(startYear, startMonth, startDay, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                        while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                                            add(Calendar.DAY_OF_MONTH, -1)
                                        }
                                    }
                                    val startTime = startCalendar.timeInMillis
                                    val currentWeek = if (today < startTime) {
                                        1
                                    } else {
                                        val diff = today - startTime
                                        ((diff / (7 * 24 * 60 * 60 * 1000)).toInt() + 1).coerceIn(1, 20)
                                    }
                                    
                                    viewModel.setCurrentWeek(currentWeek)
                                    
                                    // 一并导入其它信息（失败不影响课表导入）
                                    var examCount = if (!examHtml.isNullOrEmpty())
                                        OtherInfoImporter.importExams(context, examHtml, finalScheduleId, examXnxqid) else 0
                                    var planCount = if (!planHtml.isNullOrEmpty())
                                        OtherInfoImporter.importTrainingPlans(context, planHtml, finalScheduleId) else 0
                                    var allPlanCount = if (!allPlanHtml.isNullOrEmpty())
                                        OtherInfoImporter.importTrainingPlansAll(context, allPlanHtml, finalScheduleId) else 0
                                    var scoreCount = if (!scoreHtml.isNullOrEmpty())
                                        OtherInfoImporter.importScores(context, scoreHtml, finalScheduleId) else 0
                                    
                                    withContext(Dispatchers.Main) {
                                        val parts = mutableListOf("成功导入 ${parseResult.courses.size} 门课程")
                                        if (examCount > 0) parts.add("$examCount 条考试安排")
                                        if (planCount > 0) parts.add("$planCount 条培养方案")
                                        if (allPlanCount > 0) parts.add("$allPlanCount 条课程设置")
                                        if (scoreCount > 0) parts.add("$scoreCount 条成绩")
                                        val msg = parts.joinToString("，")
                                        AndroidToast.makeText(
                                            context,
                                            msg,
                                            AndroidToast.LENGTH_SHORT
                                        ).show()
                                    }
                                    
                                    onNavigateBack()
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        AndroidToast.makeText(
                                            context,
                                            "导入失败: ${e.message}",
                                            AndroidToast.LENGTH_LONG
                                        ).show()
                                    }
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        enabled = !isLoading && 
                                 (selectedScheduleOption == "new" && finalScheduleName.isNotBlank() || 
                                  selectedScheduleOption == "existing" && schedules.isNotEmpty()),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = hyperPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("确认导入", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
    
    // 日期选择器对话框
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = remember {
                val cal = Calendar.getInstance()
                cal.set(startYear, startMonth, startDay)
                cal.timeInMillis
            },
            yearRange = currentYear - 1..currentYear + 1
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = dateMillis
                            startYear = cal.get(Calendar.YEAR)
                            startMonth = cal.get(Calendar.MONTH)
                            startDay = cal.get(Calendar.DAY_OF_MONTH)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确定", color = hyperPrimary)
                }
            },
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = hyperPrimary
            )
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
