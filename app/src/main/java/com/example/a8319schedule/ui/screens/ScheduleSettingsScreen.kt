package com.example.a8319schedule.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.a8319schedule.data.PeriodTime
import com.example.a8319schedule.data.ScheduleSettingsManager
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import com.example.a8319schedule.ui.components.HyperOSAlertDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import top.yukonga.miuix.kmp.basic.*

@Composable
fun ScheduleSettingsScreen(
    settingsManager: ScheduleSettingsManager,
    scheduleViewModel: ScheduleViewModel,
    onNavigateBack: () -> Unit,
    onSaveComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    val settings by settingsManager.settings.collectAsState(initial = null)
    val activeSchedule by scheduleViewModel.activeSchedule.collectAsState()
    
    val initialPeriodTimes = ScheduleSettingsManager.DEFAULT_TIMES
    var periodTimes by remember { mutableStateOf(initialPeriodTimes) }
    
    val initialStartDate = remember(activeSchedule) {
        val currentActiveSchedule = activeSchedule
        if (currentActiveSchedule != null && currentActiveSchedule.startDate > 0) {
            Date(currentActiveSchedule.startDate)
        } else {
            Date()
        }
    }
    var startDate by remember { mutableStateOf(initialStartDate) }
    
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HyperOSScreenTopBar(
                title = "课表设置",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 学期日期设置
            Text(
                text = "学期日期",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // 开始日期
            HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("学期开始日期:")
                    Row {
                        Text(
                            text = dateFormat.format(startDate),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                showDatePicker = true
                            }
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val activeSchedule = scheduleViewModel.activeSchedule.value
                                    if (activeSchedule != null && activeSchedule.startDate > 0) {
                                        startDate = Date(activeSchedule.startDate)
                                        Toast.makeText(context, "已刷新开学日期为: ${dateFormat.format(startDate)}", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "当前课表未设置开学日期", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, "刷新")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // 课程时间设置
            Text(
                text = "课程时间",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "提示: 点击时间可以编辑",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(periodTimes) { periodTime ->
                    PeriodTimeItem(
                        periodTime = periodTime,
                        onTimeChanged = { start, end ->
                            periodTimes = periodTimes.map {
                                if (it.period == periodTime.period) {
                                    it.copy(startTime = start, endTime = end)
                                } else {
                                    it
                                }
                            }
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    scope.launch {
                        val calendar = Calendar.getInstance().apply {
                            time = startDate
                            add(Calendar.WEEK_OF_YEAR, 20)
                        }
                        
                        val activeSchedule = scheduleViewModel.activeSchedule.value
                        if (activeSchedule == null) {
                            Toast.makeText(context, "没有激活的课表，无法设置开学日期", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        
                        val activeScheduleId = activeSchedule.id
                        scheduleViewModel.updateStartDate(activeScheduleId, startDate.time)
                        
                        kotlinx.coroutines.delay(300)
                        
                        settingsManager.saveAllPeriodTimes(periodTimes)
                        
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                        
                        kotlinx.coroutines.delay(700)
                        
                        onSaveComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                cornerRadius = 12.dp
            ) {
                Text("保存设置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    
    HyperOSDatePickerDialog(
        visible = showDatePicker,
        onDateSelected = { date ->
            startDate = date
            showDatePicker = false
        },
        onDismiss = { showDatePicker = false },
        initialDate = startDate
    )
}

@Composable
fun PeriodTimeItem(
    periodTime: PeriodTime,
    onTimeChanged: (String, String) -> Unit
) {
    var showTimeEditDialog by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf("start") }
    
    HyperOSCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${periodTime.period} 节",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            
            Row(
                modifier = Modifier.weight(2f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = periodTime.startTime,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        editMode = "start"
                        showTimeEditDialog = true
                    }
                )
                
                Text("-", modifier = Modifier.align(Alignment.CenterVertically))
                
                Text(
                    text = periodTime.endTime,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        editMode = "end"
                        showTimeEditDialog = true
                    }
                )
            }
        }
    }
    
    TimeEditDialog(
        visible = showTimeEditDialog,
        currentTime = if (editMode == "start") periodTime.startTime else periodTime.endTime,
        onTimeConfirmed = { newTime ->
            if (editMode == "start") {
                onTimeChanged(newTime, periodTime.endTime)
            } else {
                onTimeChanged(periodTime.startTime, newTime)
            }
            showTimeEditDialog = false
        },
        onDismiss = { showTimeEditDialog = false }
    )
}

@Composable
fun HyperOSDatePickerDialog(
    visible: Boolean,
    onDateSelected: (Date) -> Unit,
    onDismiss: () -> Unit,
    initialDate: Date = Date()
) {
    val calendar = Calendar.getInstance().apply {
        time = initialDate
    }
    var year by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var day by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var showError by remember { mutableStateOf(false) }
    
    HyperOSAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "选择日期",
        onConfirm = {
            if (year <= 0 || month < 0 || month > 11 || day <= 0) {
                showError = true
            } else {
                try {
                    val cal = Calendar.getInstance()
                    cal.set(year, month, 1)
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    
                    if (day > maxDay) {
                        showError = true
                        return@HyperOSAlertDialog
                    }
                    
                    cal.set(year, month, day)
                    onDateSelected(cal.time)
                } catch (e: Exception) {
                    showError = true
                }
            }
        }
    ) {
        if (showError) {
            Text(
                text = "请完整填写日期信息",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("年: ", modifier = Modifier.width(60.dp))
            TextField(
                value = if (year > 0) year.toString() else "",
                onValueChange = { 
                    year = if (it.isEmpty()) 0 else it.toIntOrNull() ?: year
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
                singleLine = true,
                cornerRadius = 8.dp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("月: ", modifier = Modifier.width(60.dp))
            TextField(
                value = if (month >= 0 && month < 12) (month + 1).toString() else "",
                onValueChange = { 
                    month = if (it.isEmpty()) -1 else (it.toIntOrNull()?.minus(1) ?: month).coerceIn(0, 11)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
                singleLine = true,
                cornerRadius = 8.dp
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("日: ", modifier = Modifier.width(60.dp))
            TextField(
                value = if (day > 0) day.toString() else "",
                onValueChange = { 
                    day = if (it.isEmpty()) 0 else it.toIntOrNull() ?: day
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(100.dp),
                singleLine = true,
                cornerRadius = 8.dp
            )
        }
    }
}

@Composable
fun TimeEditDialog(
    visible: Boolean,
    currentTime: String,
    onTimeConfirmed: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var hour by remember { mutableStateOf(currentTime.substringBefore(":")) }
    var minute by remember { mutableStateOf(currentTime.substringAfter(":")) }
    
    HyperOSAlertDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = "编辑时间",
        onConfirm = {
            val hourValue = hour.toIntOrNull()?.coerceIn(0, 23) ?: 0
            val minuteValue = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
            onTimeConfirmed("${hourValue.toString().padStart(2, '0')}:${minuteValue.toString().padStart(2, '0')}")
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextField(
                value = hour,
                onValueChange = { 
                    if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                        hour = it
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
                value = minute,
                onValueChange = { 
                    if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                        minute = it
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
