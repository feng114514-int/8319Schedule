package com.example.a8319schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.data.Exam
import com.example.a8319schedule.data.ExamParser
import com.example.a8319schedule.ui.components.HyperOSAlertDialog
import com.example.a8319schedule.ui.components.SwipeToActionBox
import com.example.a8319schedule.viewmodel.ExamViewModel
import top.yukonga.miuix.kmp.basic.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExamListScreen(
    onNavigateBack: () -> Unit,
    examViewModel: ExamViewModel = viewModel()
) {
    val exams by examViewModel.exams.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

    var dialogVisible by remember { mutableStateOf(false) }
    var editingExam by remember { mutableStateOf<Exam?>(null) }

    fun openAdd() {
        editingExam = null
        dialogVisible = true
    }

    fun openEdit(exam: Exam) {
        editingExam = exam
        dialogVisible = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // 顶部导航栏 - 与 MyScreen 统一的 HyperOS 风格
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 0.dp
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
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "考试安排",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (exams.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无考试安排",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击下方按钮添加，或导入课表自动获取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    AddExamButton(primaryColor = primaryColor, onClick = { openAdd() })
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(exams, key = { it.id }) { exam ->
                    SwipeToActionBox(
                        onEdit = { openEdit(exam) },
                        onDelete = { examViewModel.deleteExam(exam.id) }
                    ) {
                        ExamCard(exam = exam, primaryColor = primaryColor)
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AddExamButton(primaryColor = primaryColor, onClick = { openAdd() })
                    }
                }
            }
        }
    }

    // 添加 / 编辑对话框（仅在可见时进入 composition，确保每次打开状态重置）
    if (dialogVisible) {
        ExamEditDialog(
            exam = editingExam,
            onDismiss = { dialogVisible = false },
            onConfirm = { exam ->
                if (editingExam == null) {
                    examViewModel.addExam(exam)
                } else {
                    examViewModel.updateExam(exam)
                }
                dialogVisible = false
            }
        )
    }
}

/**
 * 圆形 + 号按钮
 */
@Composable
private fun AddExamButton(primaryColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(primaryColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "添加考试",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun ExamCard(exam: Exam, primaryColor: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 课程名称
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = exam.courseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface,
                    modifier = Modifier.weight(1f)
                )
                // 倒计时
                CountdownBadge(examStartTs = exam.examStartTimestamp, primaryColor = primaryColor)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 考试时间
            ExamInfoRow(
                icon = Icons.Default.Event,
                text = formatExamTime(exam.examTimeRaw, exam.examStartTimestamp),
                color = onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 考场 + 座位号
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExamInfoRow(
                    icon = Icons.Default.Place,
                    text = "考场：${exam.examRoom.ifBlank { "未公布" }}",
                    color = onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                ExamInfoRow(
                    icon = Icons.Default.Chair,
                    text = "座位：${exam.seatNumber.ifBlank { "未分配" }}",
                    color = onSurfaceVariant
                )
            }

            if (exam.teacher.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                ExamInfoRow(
                    icon = Icons.Default.School,
                    text = "授课：${exam.teacher}",
                    color = onSurfaceVariant
                )
            }
            if (exam.invigilator.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                ExamInfoRow(
                    icon = Icons.Default.Person,
                    text = "监考：${exam.invigilator}",
                    color = onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ExamInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            icon,
            contentDescription = null,
            tint = color.copy(alpha = 0.7f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}

@Composable
private fun CountdownBadge(examStartTs: Long, primaryColor: Color) {
    if (examStartTs <= 0L) return
    val now = System.currentTimeMillis()
    if (examStartTs < now) return

    val days = ((examStartTs - now) / (24 * 60 * 60 * 1000)).toInt()
    val label = when {
        days <= 0 -> "今天"
        days == 1 -> "明天"
        days < 30 -> "${days}天"
        else -> "${days / 30}个月"
    }
    Surface(
        color = primaryColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = "还有 $label",
            fontSize = 12.sp,
            color = primaryColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun formatExamTime(raw: String, timestamp: Long): String {
    // 原始格式 "2026-06-29 10:25~12:05" 转为 "6月29日 10:25-12:05"
    return try {
        if (timestamp > 0) {
            val date = Date(timestamp)
            val monthDay = SimpleDateFormat("M月d日", Locale.getDefault()).format(date)
            val timeRange = raw.substringAfter(" ", "")
            "$monthDay $timeRange".replace("~", "-")
        } else {
            raw
        }
    } catch (e: Exception) {
        raw
    }
}

/**
 * 添加 / 编辑考试对话框
 * @param exam null 表示添加，非空表示编辑（预填该考试数据）
 */
@Composable
private fun ExamEditDialog(
    exam: Exam?,
    onDismiss: () -> Unit,
    onConfirm: (Exam) -> Unit
) {
    // 用 exam?.id 作为 key，确保编辑不同考试时状态重置
    var courseName by remember(exam?.id) {
        mutableStateOf(exam?.courseName ?: "")
    }
    var date by remember(exam?.id) {
        mutableStateOf(
            exam?.examStartTimestamp?.takeIf { it > 0 }?.let { Date(it) } ?: Date()
        )
    }
    // 从 examTimeRaw "yyyy-MM-dd HH:mm~HH:mm" 提取开始/结束时间
    var startTime by remember(exam?.id) {
        mutableStateOf(exam?.examTimeRaw?.substringAfter(" ", "")?.substringBefore("~", "") ?: "")
    }
    var endTime by remember(exam?.id) {
        mutableStateOf(exam?.examTimeRaw?.substringAfter("~", "")?.trim() ?: "")
    }
    var examRoom by remember(exam?.id) { mutableStateOf(exam?.examRoom ?: "") }
    var seatNumber by remember(exam?.id) { mutableStateOf(exam?.seatNumber ?: "") }
    var teacher by remember(exam?.id) { mutableStateOf(exam?.teacher ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val timeRegex = Regex("^\\d{2}:\\d{2}$")
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    HyperOSAlertDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = if (exam == null) "添加考试" else "编辑考试",
        confirmText = if (exam == null) "添加" else "保存",
        onConfirm = {
            // 校验
            when {
                courseName.isBlank() -> {
                    errorMsg = "请输入课程名称"
                    return@HyperOSAlertDialog
                }
                !timeRegex.matches(startTime) -> {
                    errorMsg = "开始时间格式应为 HH:mm，如 08:30"
                    return@HyperOSAlertDialog
                }
                !timeRegex.matches(endTime) -> {
                    errorMsg = "结束时间格式应为 HH:mm，如 10:30"
                    return@HyperOSAlertDialog
                }
            }

            // 拼时间串并解析时间戳
            val dateStr = dateFmt.format(date)
            val timeRaw = "$dateStr $startTime~$endTime"
            val (startTs, endTs) = ExamParser.parseTime(timeRaw)

            if (startTs <= 0) {
                errorMsg = "时间解析失败，请检查输入"
                return@HyperOSAlertDialog
            }

            // 构造 Exam：添加时用空模板，编辑时保留原 id/scheduleId
            val base = exam ?: Exam(
                scheduleId = 0,
                courseName = "",
                courseCode = "",
                sessionName = "",
                campus = "",
                teacher = "",
                invigilator = "",
                examTimeRaw = "",
                examStartTimestamp = 0,
                examEndTimestamp = 0,
                examRoom = "",
                seatNumber = "",
                admissionTicket = "",
                remark = "",
                xnxqid = ""
            )
            val result = base.copy(
                courseName = courseName.trim(),
                examTimeRaw = timeRaw,
                examStartTimestamp = startTs,
                examEndTimestamp = endTs,
                examRoom = examRoom.trim(),
                seatNumber = seatNumber.trim(),
                teacher = teacher.trim()
            )
            errorMsg = null
            onConfirm(result)
        }
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // 错误提示
            errorMsg?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // 课程名称
            DialogField(
                label = "课程名称",
                value = courseName,
                onValueChange = { courseName = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 考试日期（点击弹出日期选择器）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "考试日期",
                    modifier = Modifier.width(72.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = dateFmt.format(date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 开始 / 结束时间
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开始时间",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        singleLine = true,
                        cornerRadius = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "结束时间",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    TextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        singleLine = true,
                        cornerRadius = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "格式：HH:mm，如 08:30",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 考场
            DialogField(
                label = "考场",
                value = examRoom,
                onValueChange = { examRoom = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 座位号
            DialogField(
                label = "座位号",
                value = seatNumber,
                onValueChange = { seatNumber = it }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 授课教师
            DialogField(
                label = "授课教师",
                value = teacher,
                onValueChange = { teacher = it }
            )
        }
    }

    // 日期选择器（同包 public 函数，可直接调用）
    HyperOSDatePickerDialog(
        visible = showDatePicker,
        onDateSelected = {
            date = it
            showDatePicker = false
        },
        onDismiss = { showDatePicker = false },
        initialDate = date
    )
}

/**
 * 对话框内单行表单字段：标签 + 输入框
 */
@Composable
private fun DialogField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cornerRadius = 8.dp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
