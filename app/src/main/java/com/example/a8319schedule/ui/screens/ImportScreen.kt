package com.example.a8319schedule.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedCallback
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.data.ScheduleSettingsManager
import com.example.a8319schedule.data.TimetableParser
import com.example.a8319schedule.ui.components.ImportOptionDialog
import com.example.a8319schedule.viewmodel.CourseViewModel
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*

@Composable
fun ImportScreen(
    viewModel: CourseViewModel,
    settingsManager: ScheduleSettingsManager,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val scheduleViewModel: ScheduleViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    
    var parseResult by remember { mutableStateOf<TimetableParser.ParseResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showWebView by remember { mutableStateOf(false) }
    var showSchoolSelection by remember { mutableStateOf(false) }
    var showImportSettings by remember { mutableStateOf(false) }
    var importSettingsScheduleId by remember { mutableStateOf(0L) }
    var importSettingsScheduleName by remember { mutableStateOf("") }
    var importSettingsIsNewSchedule by remember { mutableStateOf(false) }
    var webViewUrl by remember { mutableStateOf("") }
    var webViewParserType by remember { mutableStateOf("") }
    // 导入课表时一并抓取的考试安排 HTML 及其学期标识，待确认导入时入库
    var pendingExamHtml by remember { mutableStateOf<String?>(null) }
    var pendingExamXnxqid by remember { mutableStateOf("") }
    // 导入课表时一并抓取的培养方案（执行计划）HTML，待确认导入时入库
    var pendingPlanHtml by remember { mutableStateOf<String?>(null) }
    // 导入课表时一并抓取的培养方案（课程设置总表）HTML，待确认导入时入库
    var pendingAllPlanHtml by remember { mutableStateOf<String?>(null) }
    // 导入课表时一并抓取的成绩查询 HTML，待确认导入时入库
    var pendingScoreHtml by remember { mutableStateOf<String?>(null) }
    // 导入课表时一并抓取的教学周历 HTML，用于自动识别开学日期
    var pendingWeekHtml by remember { mutableStateOf<String?>(null) }
    
    val schedules by scheduleViewModel.schedules.collectAsState()
    val activeSchedule by scheduleViewModel.activeSchedule.collectAsState()
    val activeScheduleId by scheduleViewModel.activeScheduleId.collectAsState()

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isLoading = true
            scope.launch {
                val result = importFromFile(context, uri)
                parseResult = result
                isLoading = false
                showResultDialog = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            fileLauncher.launch(arrayOf("text/html", "*/*"))
        } else {
            Toast.makeText(context, "需要存储权限才能读取文件", Toast.LENGTH_SHORT).show()
        }
    }

    if (showResultDialog && parseResult != null) {
        ImportResultDialog(
            result = parseResult!!,
            onDismiss = { showResultDialog = false },
            onConfirm = {
                if (parseResult != null) {
                    importSettingsScheduleId = 0L
                    importSettingsScheduleName = ""
                    importSettingsIsNewSchedule = false
                    showResultDialog = false
                    showImportSettings = true
                }
            }
        )
    }

    if (showSchoolSelection) {
        SchoolSelectionScreen(
            onNavigateBack = { showSchoolSelection = false },
            onNavigateToWebViewImport = { url, parserType ->
                webViewUrl = url
                webViewParserType = parserType
                showSchoolSelection = false
                showWebView = true
            }
        )
        return
    }

    if (showWebView) {
        WebViewImportScreen(
            onNavigateBack = { showWebView = false },
            onImportSuccess = { text ->
                isLoading = true
                scope.launch {
                    try {
                        // 从合并 JSON 中提取考试安排数据
                        val jsonObj = try { org.json.JSONObject(text) } catch (e: Exception) { null }
                        val examHtml = jsonObj?.optString("examHtml", "") ?: ""
                        val examXnxqid = jsonObj?.optString("xnxqid", "") ?: ""
                        pendingExamHtml = if (examHtml.isNotEmpty()) examHtml else null
                        pendingExamXnxqid = examXnxqid
                        val planHtml = jsonObj?.optString("planHtml", "") ?: ""
                        pendingPlanHtml = if (planHtml.isNotEmpty()) planHtml else null
                        val allPlanHtml = jsonObj?.optString("allPlanHtml", "") ?: ""
                        pendingAllPlanHtml = if (allPlanHtml.isNotEmpty()) allPlanHtml else null
                        val scoreHtml = jsonObj?.optString("scoreHtml", "") ?: ""
                        pendingScoreHtml = if (scoreHtml.isNotEmpty()) scoreHtml else null
                        // 教学周历（用于自动识别开学日期）
                        val weekHtml = jsonObj?.optString("weekHtml", "") ?: ""
                        pendingWeekHtml = if (weekHtml.isNotEmpty()) weekHtml else null

                        val result = parseScheduleJson(text)
                        if (result != null) {
                            parseResult = result
                            isLoading = false
                            showWebView = false
                            importSettingsScheduleId = 0L
                            importSettingsScheduleName = ""
                            importSettingsIsNewSchedule = false
                            showImportSettings = true
                        } else {
                            Toast.makeText(context, "解析失败: 未能解析课程数据", Toast.LENGTH_LONG).show()
                            isLoading = false
                            showWebView = false
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "解析失败: ${e.message}", Toast.LENGTH_LONG).show()
                        isLoading = false
                        showWebView = false
                    }
                }
            },
            initialUrl = webViewUrl,
            parserType = webViewParserType
        )
        return
    }

    Scaffold(
        topBar = {
            HyperOSScreenTopBar(
                title = "导入课表",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在解析课表...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 导入说明卡片
                    HyperOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "导入说明",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "支持从教务系统导出的课表HTML文件导入，或直接登录教务系统在线导入。",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        "选择导入方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 从文件导入卡片
                    ImportMethodCard(
                        icon = Icons.Default.Folder,
                        title = "从文件导入",
                        description = "选择已保存的课表HTML文件",
                        onClick = {
                            fileLauncher.launch(arrayOf("text/html", "*/*"))
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 在线导入卡片
                    ImportMethodCard(
                        icon = Icons.Default.Cloud,
                        title = "在线导入（推荐）",
                        description = "登录教务系统直接导入",
                        onClick = {
                            showSchoolSelection = true
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 使用说明
                    HyperOSCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "使用步骤",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            StepItem("1", "点击\"在线导入\"打开内置浏览器")
                            StepItem("2", "登录进入学校门户")
                            StepItem("3", "登录教务系统并进入\"学期理论课表\"页面")
                            StepItem("4", "点击右上角导入按钮(✓)直接导入课表")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "或使用传统方式：",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            StepItem("A", "在电脑上登录教务系统")
                            StepItem("B", "进入课表查询页面")
                            StepItem("C", "保存网页为HTML文件（选择\"网页，全部\"选项）")
                            StepItem("D", "点击\"从文件导入\"选择文件")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        "支持的教务系统：江西理工大学",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    if (showImportSettings && parseResult != null) {
        SimpleImportSettingsScreen(
            viewModel = viewModel,
            scheduleViewModel = scheduleViewModel,
            settingsManager = settingsManager,
            parseResult = parseResult!!,
            scheduleId = importSettingsScheduleId,
            scheduleName = importSettingsScheduleName,
            isNewSchedule = importSettingsIsNewSchedule,
            examHtml = pendingExamHtml,
            examXnxqid = pendingExamXnxqid,
            planHtml = pendingPlanHtml,
            allPlanHtml = pendingAllPlanHtml,
            scoreHtml = pendingScoreHtml,
            weekHtml = pendingWeekHtml,
            onNavigateBack = {
                showImportSettings = false
                pendingExamHtml = null
                pendingExamXnxqid = ""
                pendingPlanHtml = null
                pendingAllPlanHtml = null
                pendingScoreHtml = null
                pendingWeekHtml = null
                onNavigateBack()
            }
        )
    }
}

@Composable
fun HyperOSScreenTopBar(
    title: String,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Row { actions() }
        }
    }
}

@Composable
private fun ImportMethodCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { rotationZ = 180f }
            )
        }
    }
}

@Composable
private fun StepItem(number: String, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ImportResultDialog(
    result: TimetableParser.ParseResult,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (result.success) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                if (result.success) "解析成功" else "解析失败",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                Text(result.message)
                
                if (result.success && result.courses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (result.semesterInfo.isNotEmpty()) {
                        Text(
                            "学期：${result.semesterInfo}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    Text(
                        "课程列表：",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    result.courses.take(5).forEach { course ->
                        Text(
                            "• ${course.name} (${course.teacher})",
                            fontSize = 13.sp
                        )
                    }
                    
                    if (result.courses.size > 5) {
                        Text(
                            "...还有 ${result.courses.size - 5} 门课程",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = result.success) {
                Text(if (result.success) "确认导入" else "关闭")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private suspend fun parseScheduleJson(jsonText: String): TimetableParser.ParseResult? {
    return withContext(Dispatchers.IO) {
        try {
            val jsonObject = org.json.JSONObject(jsonText)
            val success = jsonObject.optBoolean("success", false)
            
            if (!success) {
                val error = jsonObject.optString("error", "未知错误")
                return@withContext TimetableParser.ParseResult(
                    courses = emptyList(),
                    success = false,
                    message = error
                )
            }
            
            val scheduleArray = jsonObject.optJSONArray("schedule") ?: return@withContext null
            val courses = mutableListOf<Course>()
            
            val courseColors = com.example.a8319schedule.data.CourseColors.PALETTE
            
            val colorMap = mutableMapOf<String, Long>()
            var colorIndex = 0
            
            for (i in 0 until scheduleArray.length()) {
                val courseJson = scheduleArray.getJSONObject(i)
                val name = courseJson.optString("name", "")
                val teacher = courseJson.optString("teacher", "")
                val location = courseJson.optString("location", "")
                val weekDay = courseJson.optString("weekDay", "")
                val timeSlot = courseJson.optString("timeSlot", "")
                val weeks = courseJson.optString("weeks", "")
                
                if (name.isNotEmpty()) {
                    val dayOfWeek = when (weekDay) {
                        "星期一" -> 1
                        "星期二" -> 2
                        "星期三" -> 3
                        "星期四" -> 4
                        "星期五" -> 5
                        "星期六" -> 6
                        "星期日" -> 7
                        "星期七" -> 7
                        else -> {
                            try {
                                weekDay.toIntOrNull() ?: 1
                            } catch (e: Exception) {
                                1
                            }
                        }
                    }
                    
                    val bigPeriod = try {
                        timeSlot.substringAfter("第").substringBefore("大节").toInt()
                    } catch (e: Exception) {
                        try {
                            timeSlot.filter { it.isDigit() }.toIntOrNull() ?: 1
                        } catch (ex: Exception) {
                            1
                        }
                    }
                    
                    val startPeriod = (bigPeriod - 1) * 2 + 1
                    val endPeriod = bigPeriod * 2
                    
                    val color = colorMap.getOrPut(name) {
                        val c = courseColors[colorIndex % courseColors.size]
                        colorIndex++
                        c
                    }
                    
                    // 节次：优先使用解析器给出的精确节次（如 [03-04节]）
                    val startSection = courseJson.optInt("startSection", 0)
                    val endSection = courseJson.optInt("endSection", 0)
                    val finalStartPeriod = if (startSection > 0) startSection else startPeriod
                    val finalEndPeriod = if (endSection > 0 && endSection >= finalStartPeriod) endSection else endPeriod

                    // 周次：优先使用无损数组 weeksList，其次解析紧凑区间串（如 "1,3,5,7,9-16"）
                    val weekNumbers = mutableListOf<Int>()
                    val weeksArray = courseJson.optJSONArray("weeksList")
                    if (weeksArray != null && weeksArray.length() > 0) {
                        for (w in 0 until weeksArray.length()) {
                            val week = weeksArray.optInt(w, -1)
                            if (week > 0) weekNumbers.add(week)
                        }
                    } else {
                        weekNumbers.addAll(TimetableParser.parseWeekExpression(weeks))
                    }
                    if (weekNumbers.isEmpty()) {
                        // 解析不出周次时兜底为 1-16 周，保持原有行为
                        weekNumbers.addAll(1..16)
                    }

                    val courseGroupId = "${name}_${teacher}_${location}_${dayOfWeek}_${bigPeriod}"

                    // 周次不连续时（单双周）只在对应的周生成记录
                    for (weekNumber in weekNumbers.distinct().sorted()) {
                        val course = Course(
                            name = name,
                            teacher = teacher,
                            classroom = location,
                            dayOfWeek = dayOfWeek,
                            weekNumber = weekNumber,
                            startPeriod = finalStartPeriod,
                            endPeriod = finalEndPeriod,
                            color = color,
                            courseGroupId = courseGroupId,
                            courseInstanceId = "${courseGroupId}_${weekNumber}",
                            scheduleId = 0L
                        )
                        courses.add(course)
                    }
                }
            }
            
            return@withContext TimetableParser.ParseResult(
                courses = courses,
                success = true,
                message = "成功解析 ${courses.size} 门课程"
            )
        } catch (e: Exception) {
            return@withContext null
        }
    }
}

private suspend fun importFromFile(context: Context, uri: Uri): TimetableParser.ParseResult {
    return withContext(Dispatchers.IO) {
        try {
            val html = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: return@withContext TimetableParser.ParseResult(
                courses = emptyList(),
                success = false,
                message = "无法读取文件"
            )
            
            TimetableParser.parse(html)
        } catch (e: Exception) {
            TimetableParser.ParseResult(
                courses = emptyList(),
                success = false,
                message = "读取文件失败: ${e.message}"
            )
        }
    }
}
