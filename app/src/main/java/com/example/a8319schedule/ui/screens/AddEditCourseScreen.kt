package com.example.a8319schedule.ui.screens

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.data.CourseConflictDetector
import com.example.a8319schedule.data.ScheduleSettingsManager
import com.example.a8319schedule.viewmodel.CourseViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditCourseScreen(
    viewModel: CourseViewModel,
    courseId: Long?,
    onNavigateBack: () -> Unit,
    showControls: Boolean = true,
    onEditThisWeek: ((Long, String, Int) -> Unit)? = null,
    settingsManager: ScheduleSettingsManager? = null
) {
    val scope = rememberCoroutineScope()
    
    suspend fun getCurrentWeek(): Int {
        return try {
            settingsManager?.let { manager ->
                val settings = manager.settings.first()
                manager.getCurrentWeek(settings)
            } ?: run {
                val calendar = Calendar.getInstance()
                val currentYear = calendar.get(Calendar.YEAR)
                val currentMonth = calendar.get(Calendar.MONTH)
                
                val semesterStartYear = if (currentMonth >= Calendar.FEBRUARY && currentMonth <= Calendar.JULY) {
                    currentYear - 1
                } else {
                    currentYear
                }
                
                val startOfMonth = Calendar.getInstance().apply {
                    set(Calendar.YEAR, semesterStartYear)
                    set(Calendar.MONTH, Calendar.SEPTEMBER)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                
                val diffInMillis = calendar.timeInMillis - startOfMonth.timeInMillis
                val diffInDays = diffInMillis / (1000 * 60 * 60 * 24)
                val weekNumber = (diffInDays / 7).toInt() + 1
                
                weekNumber.coerceIn(1, 20)
            }
        } catch (e: Exception) {
            1
        }
    }
    
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var teacher by remember { mutableStateOf(TextFieldValue("")) }
    var classroom by remember { mutableStateOf(TextFieldValue("")) }
    var dayOfWeek by remember { mutableStateOf(1) }
    var startWeek by remember { mutableStateOf(1) }
    var endWeek by remember { mutableStateOf(16) }
    var startPeriod by remember { mutableStateOf(1) }
    var endPeriod by remember { mutableStateOf(2) }
    var selectedColor by remember { mutableStateOf(0xFF4CAF50) }
    
    // 冲突检测状态
    var conflictDialogVisible by remember { mutableStateOf(false) }
    var conflictMessage by remember { mutableStateOf("") }
    var pendingSave by remember { mutableStateOf(false) }
    
    // HyperOS 风格配色
    val hyperPrimary = Color(0xFF1A73E8)
    val hyperSurface = Color.White
    val hyperOnSurface = Color(0xFF1F1F1F)
    val hyperOnSurfaceVariant = Color(0xFF757575)
    val hyperBackground = Color(0xFFF5F5F5)
    
    val scale by animateFloatAsState(
        targetValue = if (showControls) 1f else 0.3f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    var currentCourseInstanceId by remember { mutableStateOf("") }
    
    LaunchedEffect(courseId) {
        if (courseId != null) {
            val course = viewModel.getCourseById(courseId)
            if (course != null) {
                name = TextFieldValue(course.name)
                teacher = TextFieldValue(course.teacher)
                classroom = TextFieldValue(course.classroom)
                dayOfWeek = course.dayOfWeek
                startWeek = course.weekNumber
                endWeek = course.weekNumber
                startPeriod = course.startPeriod
                endPeriod = course.endPeriod
                selectedColor = course.color
                currentCourseInstanceId = course.courseInstanceId
            }
        }
    }
    
    val courseColors = com.example.a8319schedule.data.CourseColors.PALETTE
    
    val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    
    fun saveCourse() {
        if (name.text.isBlank()) return
        
        scope.launch {
            val activeScheduleId = viewModel.activeScheduleId.value
            val existingCourses = viewModel.allCourses.value
            
            if (courseId != null) {
                // 编辑模式：检测冲突，排除自身
                val updatedCourse = viewModel.getCourseById(courseId) ?: return@launch
                val conflicts = CourseConflictDetector.detectConflictsForWeekRange(
                    name = name.text,
                    dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod,
                    startWeek = updatedCourse.weekNumber,
                    endWeek = updatedCourse.weekNumber,
                    scheduleId = activeScheduleId,
                    existingCourses = existingCourses,
                    excludeCourseId = courseId
                )
                
                if (conflicts.isNotEmpty() && !pendingSave) {
                    conflictMessage = CourseConflictDetector.formatConflicts(conflicts)
                    conflictDialogVisible = true
                    pendingSave = true
                    return@launch
                }
                
                // 无冲突或用户确认强制保存
                pendingSave = false
                
                val course = viewModel.getCourseById(courseId)
                if (course != null) {
                    Log.d("AddEditCourse", "编辑课程: ${course.name}, 原星期${course.dayOfWeek}, 新星期$dayOfWeek")
                    
                    val effectiveGroupId = if (course.courseGroupId.isNotEmpty()) {
                        course.courseGroupId
                    } else {
                        "${course.name}_${course.teacher}_${course.classroom}_${course.startPeriod}"
                    }
                    
                    val updated = course.copy(
                        name = name.text,
                        teacher = teacher.text,
                        classroom = classroom.text,
                        dayOfWeek = dayOfWeek,
                        weekNumber = course.weekNumber,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        color = selectedColor,
                        courseGroupId = effectiveGroupId
                    )
                    
                    Log.d("AddEditCourse", "更新课程: ${updated.name}, 星期${updated.dayOfWeek}, ID: ${updated.id}")
                    viewModel.updateCourse(updated)
                    Log.d("AddEditCourse", "课程更新完成")
                }
            } else {
                // 新增模式：检测跨周冲突
                val conflicts = CourseConflictDetector.detectConflictsForWeekRange(
                    name = name.text,
                    dayOfWeek = dayOfWeek,
                    startPeriod = startPeriod,
                    endPeriod = endPeriod,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    scheduleId = activeScheduleId,
                    existingCourses = existingCourses
                )
                
                if (conflicts.isNotEmpty() && !pendingSave) {
                    conflictMessage = CourseConflictDetector.formatConflicts(conflicts)
                    conflictDialogVisible = true
                    pendingSave = true
                    return@launch
                }
                
                // 无冲突或用户确认强制保存
                pendingSave = false
                
                val courseGroupId = "${name.text}_${teacher.text}_${classroom}_${startPeriod}"
                Log.d("AddEditCourse", "添加新课程，组ID: $courseGroupId")
                
                Log.d("AddEditCourse", "使用激活课表ID: $activeScheduleId")
                
                for (week in startWeek..endWeek) {
                    val newCourse = Course(
                        name = name.text,
                        teacher = teacher.text,
                        classroom = classroom.text,
                        dayOfWeek = dayOfWeek,
                        weekNumber = week,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        color = selectedColor,
                        courseGroupId = courseGroupId,
                        courseInstanceId = "${courseGroupId}_${week}",
                        scheduleId = activeScheduleId
                    )
                    viewModel.addCourse(newCourse)
                }
                Log.d("AddEditCourse", "已添加${endWeek - startWeek + 1}个新课程实例到课表ID: $activeScheduleId")
            }
            onNavigateBack()
        }
    }
    
    fun deleteCourse() {
        if (courseId != null) {
            scope.launch {
                viewModel.deleteCourseById(courseId)
                onNavigateBack()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(hyperBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .clip(RoundedCornerShape(if (showControls) 0.dp else 24.dp))
                .background(hyperSurface)
        ) {
            // 顶部栏 - HyperOS 风格
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
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = hyperOnSurface)
                    }
                    Text(
                        text = if (courseId == null) "添加课程" else "编辑课程",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                        color = hyperOnSurface
                    )
                    if (courseId != null && showControls) {
                        TextButton(onClick = { 
                            scope.launch {
                                val currentWeek = getCurrentWeek()
                                onEditThisWeek?.invoke(courseId, currentCourseInstanceId, currentWeek)
                            }
                        }) {
                            Text("单独编辑本周", color = hyperPrimary, fontSize = 14.sp)
                        }
                        
                        TextButton(onClick = { deleteCourse() }) {
                            Text("删除", color = Color(0xFFE53935), fontSize = 14.sp)
                        }
                    }
                }
            }
            
            // 表单内容
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 课程名称
                ChineseTextFieldHyperOS(
                    value = name,
                    onValueChange = { name = it },
                    label = "课程名称 *",
                    modifier = Modifier.fillMaxWidth(),
                    hyperPrimary = hyperPrimary,
                    hyperOnSurface = hyperOnSurface,
                    hyperOnSurfaceVariant = hyperOnSurfaceVariant
                )
                
                // 授课教师
                ChineseTextFieldHyperOS(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = "授课教师",
                    modifier = Modifier.fillMaxWidth(),
                    hyperPrimary = hyperPrimary,
                    hyperOnSurface = hyperOnSurface,
                    hyperOnSurfaceVariant = hyperOnSurfaceVariant
                )
                
                // 上课地点
                ChineseTextFieldHyperOS(
                    value = classroom,
                    onValueChange = { classroom = it },
                    label = "上课地点",
                    modifier = Modifier.fillMaxWidth(),
                    hyperPrimary = hyperPrimary,
                    hyperOnSurface = hyperOnSurface,
                    hyperOnSurfaceVariant = hyperOnSurfaceVariant
                )
            
            // 星期选择 - HyperOS 风格
            Column {
                Text(
                    text = "星期",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = hyperOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    weekDays.forEachIndexed { index, day ->
                        val isSelected = dayOfWeek == index + 1
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { dayOfWeek = index + 1 },
                            color = if (isSelected) hyperPrimary else Color(0xFFF5F5F5),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.replace("周", ""),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color.White else hyperOnSurface
                                )
                            }
                        }
                    }
                }
            }
            
            // 节次选择 - HyperOS 风格
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上课节次",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = hyperOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "第$startPeriod - $endPeriod 小节",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = hyperPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                RangeSlider(
                    value = startPeriod.toFloat()..endPeriod.toFloat(),
                    onValueChange = { range ->
                        startPeriod = range.start.toInt()
                        endPeriod = range.endInclusive.toInt()
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = hyperPrimary,
                        activeTrackColor = hyperPrimary,
                        inactiveTrackColor = Color(0xFFE0E0E0)
                    )
                )
                val startBigPeriod = ((startPeriod - 1) / 2) + 1
                val endBigPeriod = ((endPeriod - 1) / 2) + 1
                Text(
                    text = "对应大节: 第$startBigPeriod - $endBigPeriod 大节",
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    color = hyperOnSurfaceVariant
                )
            }
            
            // 周次选择 - HyperOS 风格
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "上课周次",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = hyperOnSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "第$startWeek - $endWeek 周",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = hyperPrimary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                RangeSlider(
                    value = startWeek.toFloat()..endWeek.toFloat(),
                    onValueChange = { range ->
                        startWeek = range.start.toInt()
                        endWeek = range.endInclusive.toInt()
                    },
                    valueRange = 1f..20f,
                    steps = 18,
                    colors = SliderDefaults.colors(
                        thumbColor = hyperPrimary,
                        activeTrackColor = hyperPrimary,
                        inactiveTrackColor = Color(0xFFE0E0E0)
                    )
                )
            }
            
            // 颜色选择 - HyperOS 风格
            Column {
                Text(
                    text = "课程颜色",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = hyperOnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    courseColors.forEach { color ->
                        val isSelected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(color))
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .padding(3.dp)
                                            .background(Color.White, CircleShape)
                                            .padding(2.dp)
                                            .background(Color(color), CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Done,
                                    contentDescription = "已选择",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // 保存按钮 - HyperOS 风格
            Button(
                onClick = { saveCourse() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = name.text.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = hyperPrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (courseId == null) "添加课程" else "保存修改", fontWeight = FontWeight.SemiBold)
            }
            }
        }
        
        // 冲突提示弹窗
        if (conflictDialogVisible) {
            AlertDialog(
                onDismissRequest = {
                    conflictDialogVisible = false
                    pendingSave = false
                },
                title = {
                    Text(
                        "课程时间冲突",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE53935)
                    )
                },
                text = {
                    Column {
                        Text(
                            "以下时间与新课程有重叠，是否仍要添加？",
                            fontSize = 14.sp,
                            color = hyperOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            conflictMessage,
                            fontSize = 13.sp,
                            color = hyperOnSurface,
                            lineHeight = 20.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        conflictDialogVisible = false
                        saveCourse() // 强制保存
                    }) {
                        Text("仍然添加", color = Color(0xFFE53935), fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        conflictDialogVisible = false
                        pendingSave = false
                    }) {
                        Text("取消", color = hyperOnSurfaceVariant)
                    }
                },
                shape = RoundedCornerShape(16.dp),
                containerColor = Color.White
            )
        }
    }
}

@Composable
private fun ChineseTextFieldHyperOS(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    hyperPrimary: Color,
    hyperOnSurface: Color,
    hyperOnSurfaceVariant: Color
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(hyperPrimary),
        textStyle = TextStyle(
            fontSize = 16.sp,
            color = hyperOnSurface
        ),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value.text,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = { Text(label) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = hyperOnSurface,
                    unfocusedTextColor = hyperOnSurface,
                    focusedLabelColor = hyperPrimary,
                    unfocusedLabelColor = hyperOnSurfaceVariant,
                    focusedBorderColor = hyperPrimary,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    cursorColor = hyperPrimary
                )
            )
        }
    )
}
