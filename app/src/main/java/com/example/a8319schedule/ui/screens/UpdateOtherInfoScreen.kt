package com.example.a8319schedule.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.data.OtherInfoImporter
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 更新其它信息入口页：选择要单独导入的信息类型，进入 WebView 登录后导入。
 * 不碰课表，数据绑定当前激活课表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateOtherInfoScreen(
    scheduleViewModel: ScheduleViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeScheduleId by scheduleViewModel.activeScheduleId.collectAsState()
    val activeSchedule by scheduleViewModel.activeSchedule.collectAsState()

    // 当前选中的导入模式，null 表示在选择页
    var currentMode by remember { mutableStateOf<ImportMode?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary

    // 进入 WebView 导入某种信息
    currentMode?.let { mode ->
        WebViewImportScreen(
            onNavigateBack = { currentMode = null },
            onImportSuccess = { json ->
                if (isImporting) return@WebViewImportScreen
                isImporting = true
                scope.launch {
                    try {
                        val scheduleId = activeScheduleId
                        if (scheduleId <= 0) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "请先导入或创建课表", Toast.LENGTH_LONG).show()
                            }
                            isImporting = false
                            currentMode = null
                            return@launch
                        }

                        val obj = JSONObject(json)
                        val html = obj.optString("html", "")
                        val error = if (obj.isNull("error")) "" else obj.optString("error", "")
                        val xnxqid = obj.optString("xnxqid", "")

                        if (error.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "获取失败: $error", Toast.LENGTH_LONG).show()
                            }
                            isImporting = false
                            currentMode = null
                            return@launch
                        }
                        if (html.isBlank()) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "未获取到数据，可能未登录或无数据", Toast.LENGTH_LONG).show()
                            }
                            isImporting = false
                            currentMode = null
                            return@launch
                        }

                        val count = withContext(Dispatchers.IO) {
                            when (mode) {
                                ImportMode.EXAM -> OtherInfoImporter.importExams(context, html, scheduleId, xnxqid)
                                ImportMode.SCORE -> OtherInfoImporter.importScores(context, html, scheduleId)
                                ImportMode.TRAINING_PLAN -> OtherInfoImporter.importTrainingPlans(context, html, scheduleId)
                                ImportMode.TRAINING_PLAN_ALL -> OtherInfoImporter.importTrainingPlansAll(context, html, scheduleId)
                                ImportMode.SCHEDULE -> 0
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (count > 0) {
                                Toast.makeText(context, "成功导入 $count 条${mode.displayName}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "${mode.displayName}无可用数据", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        isImporting = false
                        currentMode = null
                    }
                }
            },
            importMode = mode
        )
        return
    }

    // 选择页
    Scaffold(
        topBar = {
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                    Text(
                        text = "更新其它信息",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 当前课表提示
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "数据将导入到当前课表",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (activeSchedule != null) "当前课表：${activeSchedule!!.name}"
                            else "暂无激活课表，请先导入或创建课表",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "选择要更新的信息",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OtherInfoOptionCard(
                icon = Icons.Default.Event,
                title = "考试安排",
                description = "导入本学期考试时间、考场、座位",
                primaryColor = primaryColor,
                onClick = { currentMode = ImportMode.EXAM }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OtherInfoOptionCard(
                icon = Icons.Default.Grade,
                title = "成绩查询",
                description = "导入历史成绩和绩点",
                primaryColor = primaryColor,
                onClick = { currentMode = ImportMode.SCORE }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OtherInfoOptionCard(
                icon = Icons.Default.MenuBook,
                title = "培养方案（执行计划）",
                description = "导入已执行的课程计划",
                primaryColor = primaryColor,
                onClick = { currentMode = ImportMode.TRAINING_PLAN }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OtherInfoOptionCard(
                icon = Icons.Default.List,
                title = "培养方案（课程设置总表）",
                description = "导入完整的课程设置总表",
                primaryColor = primaryColor,
                onClick = { currentMode = ImportMode.TRAINING_PLAN_ALL }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "说明：登录教务系统后，点击右上角 ✓ 即可导入对应信息，不会影响课表。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun OtherInfoOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = 180f }
            )
        }
    }
}
