package com.example.a8319schedule.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.viewmodel.CourseViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditThisWeekCourseScreen(
    viewModel: CourseViewModel,
    courseId: Long,
    courseInstanceId: String,
    weekNumber: Int,
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    var course by remember { mutableStateOf<Course?>(null) }
    
    var selectedDayOfWeek by remember { mutableStateOf(1) }
    var startPeriod by remember { mutableStateOf(1f) }
    var endPeriod by remember { mutableStateOf(2f) }
    var classroom by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var courseName by remember { mutableStateOf("") }
    var startWeek by remember { mutableStateOf(weekNumber) }
    var endWeek by remember { mutableStateOf(weekNumber) }
    
    var isEditingThisWeekOnly by remember { mutableStateOf(false) }
    
    // HyperOS 风格配色
    val hyperBackground = Color(0xFFF5F5F5)
    val hyperPrimary = Color(0xFF1A73E8)
    val hyperSurface = Color.White
    val hyperOnSurface = Color(0xFF1F1F1F)
    val hyperOnSurfaceVariant = Color(0xFF757575)
    val hyperPrimaryContainer = Color(0xFFE8F0FE)
    val hyperError = Color(0xFFE53935)
    
    LaunchedEffect(courseInstanceId) {
        val currentCourse = viewModel.getCourseByInstanceId(courseInstanceId)
        
        course = currentCourse ?: if (courseId > 0) {
            viewModel.getCourseById(courseId)
        } else null
        
        course?.let {
            selectedDayOfWeek = it.dayOfWeek
            startPeriod = it.startPeriod.toFloat()
            endPeriod = it.endPeriod.toFloat()
            classroom = it.classroom
            teacher = it.teacher
            courseName = it.name
            
            isEditingThisWeekOnly = true
            startWeek = weekNumber
            endWeek = weekNumber
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(hyperBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部导航栏 - HyperOS 风格
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "编辑课程",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = hyperOnSurface
                        )
                        Text(
                            text = "第$weekNumber 周",
                            fontSize = 13.sp,
                            color = hyperOnSurfaceVariant
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 课程基本信息卡片 - HyperOS 风格
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = hyperSurface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .animateContentSize()
                    ) {
                        Text(
                            text = "课程信息",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = hyperOnSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 课程名称
                        OutlinedTextField(
                            value = courseName,
                            onValueChange = { courseName = it },
                            label = { Text("课程名称") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = hyperPrimary,
                                focusedLabelColor = hyperPrimary,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                cursorColor = hyperPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 星期选择
                        Text(
                            text = "上课星期",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = hyperOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (isEditingThisWeekOnly) {
                            // 单独编辑本周模式
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val days = listOf("一", "二", "三", "四", "五")
                                days.forEachIndexed { index, day ->
                                    val isSelected = selectedDayOfWeek == index + 1
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedDayOfWeek = index + 1 },
                                        color = if (isSelected) hyperPrimary else Color(0xFFF5F5F5),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "周$day",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) Color.White else hyperOnSurface
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val days = listOf("六" to 6, "日" to 7)
                                days.forEach { (day, dayIndex) ->
                                    val isSelected = selectedDayOfWeek == dayIndex
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { selectedDayOfWeek = dayIndex },
                                        color = if (isSelected) hyperPrimary else Color(0xFFF5F5F5),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                "周$day",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) Color.White else hyperOnSurface
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.weight(3f))
                            }
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "周${getDayOfWeekText(selectedDayOfWeek)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = hyperPrimary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "编辑所有周次时，星期几不可修改",
                                fontSize = 12.sp,
                                color = hyperOnSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 节次选择
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
                                text = "第${startPeriod.toInt()}-${endPeriod.toInt()}节",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = hyperPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        RangeSlider(
                            value = startPeriod..endPeriod,
                            onValueChange = { range ->
                                startPeriod = range.start
                                endPeriod = range.endInclusive
                            },
                            valueRange = 1f..10f,
                            steps = 0,
                            colors = SliderDefaults.colors(
                                thumbColor = hyperPrimary,
                                activeTrackColor = hyperPrimary,
                                inactiveTrackColor = Color(0xFFE0E0E0)
                            )
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 周次选择
                        Text(
                            text = "编辑范围",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = hyperOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val isSelectedThisWeek = isEditingThisWeekOnly
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { 
                                        startWeek = weekNumber
                                        endWeek = weekNumber
                                        isEditingThisWeekOnly = true
                                    },
                                color = if (isSelectedThisWeek) hyperPrimaryContainer else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "仅本周",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelectedThisWeek) hyperPrimary else hyperOnSurface
                                    )
                                }
                            }
                            
                            val isSelectedAllWeeks = !isEditingThisWeekOnly
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { 
                                        startWeek = 1
                                        endWeek = 20
                                        isEditingThisWeekOnly = false
                                    },
                                color = if (isSelectedAllWeeks) hyperPrimaryContainer else Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "所有周次",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isSelectedAllWeeks) hyperPrimary else hyperOnSurface
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // 教师和地点
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text("授课教师") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = hyperPrimary,
                                focusedLabelColor = hyperPrimary,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                cursorColor = hyperPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        OutlinedTextField(
                            value = classroom,
                            onValueChange = { classroom = it },
                            label = { Text("上课地点") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = hyperPrimary,
                                focusedLabelColor = hyperPrimary,
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                cursorColor = hyperPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )
                    }
                }
                
                // 操作按钮 - HyperOS 风格
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
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
                                    course?.let { original ->
                                        if (isEditingThisWeekOnly) {
                                            updateSingleWeekCourse(viewModel, original, courseName, selectedDayOfWeek, startPeriod, endPeriod, classroom, teacher, weekNumber)
                                        } else {
                                            updateAllWeeksCourses(viewModel, original, courseName, startPeriod, endPeriod, classroom, teacher)
                                        }
                                    }
                                    onNavigateBack()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = hyperPrimary
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("保存", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    
                    // 删除课程按钮
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                course?.let { original ->
                                    if (isEditingThisWeekOnly) {
                                        viewModel.deleteCourse(original)
                                    } else {
                                        deleteAllWeeksCourses(viewModel, original)
                                    }
                                    onNavigateBack()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = hyperError
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEditingThisWeekOnly) "删除本周的这门课" else "删除所有周次的这门课", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

private suspend fun updateSingleWeekCourse(
    viewModel: CourseViewModel,
    original: Course,
    name: String,
    dayOfWeek: Int,
    startPeriod: Float,
    endPeriod: Float,
    classroom: String,
    teacher: String,
    weekNumber: Int
) {
    val effectiveInstanceId = if (original.courseInstanceId.isNotEmpty()) {
        original.courseInstanceId
    } else {
        java.util.UUID.randomUUID().toString()
    }
    
    val effectiveGroupId = if (original.courseGroupId.isNotEmpty()) {
        original.courseGroupId
    } else {
        "${original.name}_${original.teacher}_${original.classroom}_${original.startPeriod}"
    }
    
    val updatedCourse = original.copy(
        name = name,
        dayOfWeek = dayOfWeek,
        startPeriod = startPeriod.toInt(),
        endPeriod = endPeriod.toInt(),
        weekNumber = weekNumber,
        classroom = classroom,
        teacher = teacher,
        color = original.color,
        courseGroupId = effectiveGroupId,
        courseInstanceId = effectiveInstanceId
    )
    
    viewModel.updateCourse(updatedCourse)
}

private suspend fun updateAllWeeksCourses(
    viewModel: CourseViewModel,
    original: Course,
    name: String,
    startPeriod: Float,
    endPeriod: Float,
    classroom: String,
    teacher: String
) {
    val effectiveGroupId = if (original.courseGroupId.isNotEmpty()) {
        original.courseGroupId
    } else {
        val newGroupId = "${original.name}_${original.teacher}_${original.classroom}_${original.startPeriod}"
        val updatedOriginal = original.copy(courseGroupId = newGroupId)
        viewModel.updateCourse(updatedOriginal)
        newGroupId
    }
    
    var groupCourses = viewModel.getCoursesByGroupId(effectiveGroupId)
    
    if (groupCourses.isEmpty()) {
        groupCourses = viewModel.getSimilarCourses(original)
        
        if (groupCourses.isNotEmpty()) {
            val updatedSimilarCourses = groupCourses.map { course ->
                course.copy(courseGroupId = effectiveGroupId)
            }
            viewModel.updateCourses(updatedSimilarCourses)
        }
    }
    
    groupCourses = viewModel.getCoursesByGroupId(effectiveGroupId)
    
    if (groupCourses.isNotEmpty()) {
        val updatedCourses = groupCourses.map { similar ->
            similar.copy(
                name = name,
                dayOfWeek = similar.dayOfWeek,
                startPeriod = startPeriod.toInt(),
                endPeriod = endPeriod.toInt(),
                classroom = classroom,
                teacher = teacher,
                weekNumber = similar.weekNumber
            )
        }
        viewModel.updateCourses(updatedCourses)
        
        kotlinx.coroutines.delay(300)
    }
}

private suspend fun deleteAllWeeksCourses(viewModel: CourseViewModel, original: Course) {
    val effectiveGroupId = if (original.courseGroupId.isNotEmpty()) {
        original.courseGroupId
    } else {
        "${original.name}_${original.teacher}_${original.classroom}_${original.startPeriod}"
    }
    
    val groupCourses = viewModel.getCoursesByGroupId(effectiveGroupId)
    viewModel.deleteCourses(groupCourses)
}

private fun getDayOfWeekText(dayOfWeek: Int): String {
    return when (dayOfWeek) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "日"
        else -> "一"
    }
}
