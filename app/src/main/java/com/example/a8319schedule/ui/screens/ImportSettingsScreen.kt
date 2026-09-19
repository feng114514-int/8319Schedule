package com.example.a8319schedule.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
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
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.data.TimetableParser
import com.example.a8319schedule.viewmodel.CourseViewModel
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast as AndroidToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSettingsScreen(
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    parseResult: TimetableParser.ParseResult,
    scheduleId: Long,
    scheduleName: String,
    isNewSchedule: Boolean,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    val schedules by scheduleViewModel.schedules.collectAsState()
    val activeSchedule by scheduleViewModel.activeSchedule.collectAsState()
    
    var scheduleDescription by remember { mutableStateOf("") }
    var finalScheduleId by remember { mutableStateOf(scheduleId) }
    var finalScheduleName by remember { mutableStateOf(scheduleName) }
    var finalIsNewSchedule by remember { mutableStateOf(isNewSchedule) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedCourses by remember { mutableStateOf(parseResult.courses) }
    var selectedScheduleOption by remember { mutableStateOf(if (isNewSchedule) "new" else "existing") }
    
    // HyperOS 风格配色
    val hyperBackground = Color(0xFFF5F5F5)
    val hyperPrimary = Color(0xFF1A73E8)
    val hyperSurface = Color.White
    val hyperOnSurface = Color(0xFF1F1F1F)
    val hyperOnSurfaceVariant = Color(0xFF757575)
    val hyperPrimaryContainer = Color(0xFFE8F0FE)
    
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
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = hyperOnSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 导入方式选择
                        Text(
                            text = "导入方式",
                            fontSize = 14.sp,
                            color = hyperOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
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
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (selectedScheduleOption == "existing") {
                            if (schedules.isNotEmpty()) {
                                Text(
                                    text = "选择要覆盖的课表",
                                    fontSize = 14.sp,
                                    color = hyperOnSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
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
                    }
                }
                
                // 课程列表卡片 - HyperOS 风格
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "课程列表",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = hyperOnSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${selectedCourses.size}门",
                                fontSize = 14.sp,
                                color = hyperPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        selectedCourses.forEach { course ->
                            CourseItemHyperOS(
                                course = course,
                                isSelected = selectedCourses.contains(course),
                                onToggle = { 
                                    selectedCourses = if (selectedCourses.contains(course)) {
                                        selectedCourses - course
                                    } else {
                                        selectedCourses + course
                                    }
                                },
                                hyperPrimary = hyperPrimary,
                                hyperOnSurface = hyperOnSurface,
                                hyperOnSurfaceVariant = hyperOnSurfaceVariant,
                                hyperPrimaryContainer = hyperPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                // 操作按钮 - HyperOS 风格
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
                                        
                                        // 必须等待创建完成并拿到真实ID，否则会写入旧课表
                                        finalScheduleId = scheduleViewModel.createScheduleAndGetId(
                                            finalScheduleName, scheduleDescription
                                        )
                                    } else {
                                        if (finalScheduleId <= 0 && schedules.isNotEmpty()) {
                                            finalScheduleId = schedules.first().id
                                            finalScheduleName = schedules.first().name
                                        }
                                    }
                                    
                                    // 原子替换：先删后插，避免异步删除与插入之间的竞态
                                    viewModel.replaceCoursesForSchedule(finalScheduleId, selectedCourses)
                                    
                                    scheduleViewModel.activateScheduleAndWait(finalScheduleId)
                                    // 课程ViewModel持有独立的激活课表ID，必须同步切换
                                    viewModel.setActiveScheduleId(finalScheduleId)
                                    
                                    withContext(Dispatchers.Main) {
                                        AndroidToast.makeText(
                                            context,
                                            "成功导入 ${selectedCourses.size} 门课程",
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
                        enabled = !isLoading && selectedCourses.isNotEmpty() && 
                                 (selectedScheduleOption == "new" && finalScheduleName.isNotBlank() || 
                                  selectedScheduleOption == "existing" && schedules.isNotEmpty()),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
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
}

@Composable
private fun CourseItemHyperOS(
    course: Course,
    isSelected: Boolean,
    onToggle: () -> Unit,
    hyperPrimary: Color,
    hyperOnSurface: Color,
    hyperOnSurfaceVariant: Color,
    hyperPrimaryContainer: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = if (isSelected) hyperPrimaryContainer else Color(0xFFF8F8F8),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = hyperPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Surface(
                    modifier = Modifier.size(20.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFE0E0E0)
                ) {}
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = course.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = hyperOnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${course.teacher} | ${course.classroom}",
                    fontSize = 13.sp,
                    color = hyperOnSurfaceVariant
                )
            }
            
            Text(
                text = "周${course.dayOfWeek} 第${course.weekNumber}周",
                fontSize = 12.sp,
                color = hyperOnSurfaceVariant
            )
        }
    }
}
