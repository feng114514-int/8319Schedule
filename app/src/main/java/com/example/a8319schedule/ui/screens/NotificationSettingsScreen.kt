package com.example.a8319schedule.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.a8319schedule.CourseAlarmScheduler
import com.example.a8319schedule.data.NotificationSettings
import com.example.a8319schedule.data.NotificationSettingsManager
import com.example.a8319schedule.data.ScheduleSettingsManager
import com.example.a8319schedule.data.SemesterSettings
import com.example.a8319schedule.data.WeekCalculator
import com.example.a8319schedule.ui.components.HyperOSAlertDialog
import com.example.a8319schedule.viewmodel.CourseViewModel
import top.yukonga.miuix.kmp.basic.*
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun NotificationSettingsScreen(
    viewModel: CourseViewModel,
    scheduleSettingsManager: ScheduleSettingsManager,
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsManager = remember { NotificationSettingsManager(context) }
    val scope = rememberCoroutineScope()
    val settings by settingsManager.settings.collectAsState(initial = NotificationSettings())
    val activeSchedule by viewModel.activeSchedule.collectAsState()
    val allCourses by viewModel.allCourses.collectAsState()
    val semesterSettings by scheduleSettingsManager.settings.collectAsState(initial = SemesterSettings(0, 0, emptyList()))
    var isImporting by remember { mutableStateOf(false) }
    var showIcsReminderPicker by remember { mutableStateOf(false) }

    // 导入课程到系统日历（生成 ICS 并交给日历 App 打开）
    fun importToCalendar(reminderMinutes: Int) {
        if (isImporting) return
        isImporting = true
        scope.launch {
            try {
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
                    startWeek = null,
                    endWeek = null,
                    calendarName = activeSchedule?.name ?: "课程表"
                )
                val icsDir = File(context.cacheDir, "ics").apply { mkdirs() }
                val icsFile = File(icsDir, "schedule_import.ics").apply {
                    writeText(icsContent)
                }
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    icsFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/calendar")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(Intent.createChooser(intent, "选择日历应用"))
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(context, "未找到可处理 .ics 的日历应用，请用导出功能", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "添加失败: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isImporting = false
            }
        }
    }

    // 通知权限
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    // 电池优化白名单
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBattery by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 从设置页返回后刷新状态
        isIgnoringBattery = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    // 自启动权限：系统无公开 API 可检测状态，由用户手动确认后记住
    val autostartPrefs = remember { context.getSharedPreferences("autostart_pref", Context.MODE_PRIVATE) }
    var autostartConfirmed by remember {
        mutableStateOf(autostartPrefs.getBoolean("confirmed", false))
    }

    // 精确闹钟权限
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    var hasExactAlarmPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else true
        )
    }

    // 提醒时间选项
    val reminderOptions = listOf(5, 10, 15, 20, 30, 45, 60)
    val reminderLabels = reminderOptions.map {
        if (it < 60) "提前 $it 分钟" else "提前 1 小时"
    }

    // 对话框显隐状态（常驻组合树以播放退出动画）
    var showReminderPicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HyperOSScreenTopBar(
                title = "通知设置",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // 通知权限提示
                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HyperOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "通知权限未开启",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "需要通知权限才能发送课程提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = "开启",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            )
                        }
                    }
                }

                // 精确闹钟权限提示（Android 12+）
                if (!hasExactAlarmPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    HyperOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "精确闹钟权限未开启",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "需要精确闹钟权限才能准时提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = "开启",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // 降级到应用详情页
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                }

                // 电池优化白名单提示
                if (!isIgnoringBattery) {
                    HyperOSCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "未关闭电池优化",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "省电策略可能杀后台导致通知失效",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Text(
                                text = "关闭",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    try {
                                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        batteryLauncher.launch(intent)
                                    } catch (e: Exception) {
                                        // 降级到电池优化设置列表
                                        try {
                                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                            batteryLauncher.launch(intent)
                                        } catch (e2: Exception) {
                                            Toast.makeText(context, "请手动在系统设置中关闭电池优化", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // 自启动权限（系统无公开 API 可检测状态，由用户确认后记住）
                val openAutostartSettings: () -> Unit = {
                    try {
                        // 尝试打开小米自启动管理
                        val intent = Intent().apply {
                            component = ComponentName(
                                "com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity"
                            )
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            // 降级：尝试华为自启动管理
                            val intent = Intent().apply {
                                component = ComponentName(
                                    "com.huawei.systemmanager",
                                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                                )
                            }
                            context.startActivity(intent)
                        } catch (e2: Exception) {
                            try {
                                // 降级：尝试 OPPO 自启动管理
                                val intent = Intent().apply {
                                    component = ComponentName(
                                        "com.coloros.safecenter",
                                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e3: Exception) {
                                try {
                                    // 降级：尝试 vivo 自启动管理
                                    val intent = Intent().apply {
                                        component = ComponentName(
                                            "com.vivo.abe",
                                            "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                                        )
                                    }
                                    context.startActivity(intent)
                                } catch (e4: Exception) {
                                    // 最终降级：打开应用详情页
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (e5: Exception) {
                                        Toast.makeText(context, "请在系统设置中开启自启动权限", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }

                HyperOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = if (autostartConfirmed)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (autostartConfirmed) Icons.Default.CheckCircle else Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = if (autostartConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (autostartConfirmed) "自启动 已开启" else "允许自启动",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (autostartConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "被杀后台后能自动恢复通知",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Text(
                            text = "去设置",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { openAutostartSettings() }
                        )
                        if (!autostartConfirmed) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "我已开启",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    autostartPrefs.edit().putBoolean("confirmed", true).apply()
                                    autostartConfirmed = true
                                    Toast.makeText(context, "已记录自启动状态", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                // ===== 上课提醒 =====
                Text(
                    text = "上课提醒",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // 总开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "上课提醒",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "课程开始前发送通知提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.courseReminderEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        settingsManager.updateSettings(settings.copy(courseReminderEnabled = enabled))
                                        CourseAlarmScheduler.rescheduleAll(context)
                                    }
                                }
                            )
                        }

                        // 提前时间选择
                        if (settings.courseReminderEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "提前提醒时间",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    modifier = Modifier.clickable { showReminderPicker = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = reminderLabels[reminderOptions.indexOf(settings.reminderMinutesBefore).coerceAtLeast(0)],
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== 每日课表摘要 =====
                Text(
                    text = "每日课表摘要",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )

                HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        // 总开关
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "每日课表摘要",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "每天早上推送今日课程概览",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = settings.dailySummaryEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch {
                                        settingsManager.updateSettings(settings.copy(dailySummaryEnabled = enabled))
                                        CourseAlarmScheduler.rescheduleAll(context)
                                    }
                                }
                            )
                        }

                        // 推送时间选择
                        if (settings.dailySummaryEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "推送时间",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    modifier = Modifier.clickable { showTimePicker = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${settings.dailySummaryHour}:${settings.dailySummaryMinute.toString().padStart(2, '0')}",
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // ===== 添加到系统日程 =====
                HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "添加到系统日程",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "导入手机日历，可选择提醒时间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (isImporting) "生成中…" else "添加",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(enabled = !isImporting) {
                                if (isImporting) return@clickable
                                showIcsReminderPicker = true
                            }
                        )
                    }
                }

                // ===== 温馨提示 =====
                HyperOSCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = CardDefaults.defaultColors(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "温馨提示",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• 开机后闹钟会自动重新注册\n• 切换课表后请重新进入本页面以更新闹钟\n• MIUI 用户还需在系统设置中允许「后台弹出界面」\n• 如果通知仍然不准时，请将应用加锁（最近任务栏下拉锁定）\n建议直接使用导入日历功能，通知功能仅具可玩性",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 提前提醒时间选择对话框
            HyperOSAlertDialog(
                visible = showReminderPicker,
                onDismissRequest = { showReminderPicker = false },
                title = "提前提醒时间",
                confirmText = "关闭",
                onConfirm = { showReminderPicker = false },
                dismissText = null
            ) {
                reminderOptions.forEachIndexed { index, minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showReminderPicker = false
                                scope.launch {
                                    settingsManager.updateSettings(
                                        settings.copy(reminderMinutesBefore = minutes)
                                    )
                                    CourseAlarmScheduler.rescheduleAll(context)
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = reminderLabels[index],
                            modifier = Modifier.weight(1f)
                        )
                        if (minutes == settings.reminderMinutesBefore) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 导入日历的提醒时间选择对话框
            val icsReminderOptions = listOf(0, 5, 10, 15, 30, 60)
            val icsReminderLabels = icsReminderOptions.map {
                if (it == 0) "无提醒" else if (it < 60) "提前 $it 分钟" else "提前 1 小时"
            }
            HyperOSAlertDialog(
                visible = showIcsReminderPicker,
                onDismissRequest = { showIcsReminderPicker = false },
                title = "日历提醒时间",
                confirmText = "取消",
                onConfirm = { showIcsReminderPicker = false },
                dismissText = null
            ) {
                icsReminderOptions.forEachIndexed { index, minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showIcsReminderPicker = false
                                importToCalendar(minutes)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = icsReminderLabels[index],
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 推送时间选择对话框
            var pickerHour by remember(showTimePicker) {
                mutableStateOf(settings.dailySummaryHour.toString().padStart(2, '0'))
            }
            var pickerMinute by remember(showTimePicker) {
                mutableStateOf(settings.dailySummaryMinute.toString().padStart(2, '0'))
            }
            HyperOSAlertDialog(
                visible = showTimePicker,
                onDismissRequest = { showTimePicker = false },
                title = "选择推送时间",
                onConfirm = {
                    val h = pickerHour.toIntOrNull()?.coerceIn(0, 23) ?: 0
                    val m = pickerMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    scope.launch {
                        settingsManager.updateSettings(
                            settings.copy(
                                dailySummaryHour = h,
                                dailySummaryMinute = m
                            )
                        )
                        CourseAlarmScheduler.rescheduleAll(context)
                    }
                    showTimePicker = false
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = pickerHour,
                        onValueChange = {
                            if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                pickerHour = it
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        label = "时",
                        singleLine = true,
                        cornerRadius = 8.dp
                    )
                    Text(":", modifier = Modifier.padding(horizontal = 8.dp))
                    TextField(
                        value = pickerMinute,
                        onValueChange = {
                            if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                                pickerMinute = it
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        label = "分",
                        singleLine = true,
                        cornerRadius = 8.dp
                    )
                }
            }
        }
    }
}
