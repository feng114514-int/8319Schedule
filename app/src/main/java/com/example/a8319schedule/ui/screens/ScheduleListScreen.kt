package com.example.a8319schedule.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import top.yukonga.miuix.kmp.basic.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun ScheduleListScreen(
    scheduleViewModel: ScheduleViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (Long) -> Unit
) {
    val schedules by scheduleViewModel.schedules.collectAsState()
    val activeScheduleId by scheduleViewModel.activeScheduleId.collectAsState()
    
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var selectedSchedule by remember { mutableStateOf<ScheduleInfo?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HyperOSScreenTopBar(
                title = "课表管理",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "创建新课表")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(schedules) { schedule ->
                ScheduleItem(
                    schedule = schedule,
                    isActive = schedule.id == activeScheduleId,
                    onActivate = { scheduleViewModel.activateSchedule(schedule.id) },
                    onClick = { 
                        if (schedule.id != activeScheduleId) {
                            scheduleViewModel.activateSchedule(schedule.id)
                        }
                    },
                    onEdit = { 
                        selectedSchedule = schedule
                        showEditDialog = true 
                    },
                    onDelete = { 
                        selectedSchedule = schedule
                        showDeleteConfirmDialog = true 
                    },
                    onSettings = { onNavigateToSettings(schedule.id) },
                    schedules = schedules
                )
            }
        }
    }
    
    if (showCreateDialog) {
        CreateScheduleDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                scheduleViewModel.createSchedule(name, description)
                showCreateDialog = false
            }
        )
    }
    
    if (showEditDialog && selectedSchedule != null) {
        EditScheduleDialog(
            schedule = selectedSchedule!!,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedSchedule ->
                scheduleViewModel.updateSchedule(updatedSchedule)
                showEditDialog = false
                selectedSchedule = null
            }
        )
    }
    
    if (showDeleteConfirmDialog && selectedSchedule != null) {
        DeleteConfirmDialog(
            scheduleName = selectedSchedule!!.name,
            onDismiss = { 
                showDeleteConfirmDialog = false
                selectedSchedule = null
            },
            onConfirm = { 
                scheduleViewModel.deleteSchedule(selectedSchedule!!)
                showDeleteConfirmDialog = false
                selectedSchedule = null
            }
        )
    }
}

@Composable
fun ScheduleItem(
    schedule: ScheduleInfo,
    isActive: Boolean,
    onActivate: () -> Unit,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSettings: () -> Unit,
    schedules: List<ScheduleInfo>
) {
    var expanded by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary
    
    HyperOSCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        colors = CardDefaults.defaultColors(
            color = if (isActive) {
                primaryColor.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = schedule.name,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) primaryColor else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = primaryColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "当前",
                                    fontSize = 10.sp,
                                    color = primaryColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    
                    if (schedule.description.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = schedule.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "创建于 ${formatDate(schedule.createdAt)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多选项",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        if (!isActive) {
                            DropdownMenuItem(
                                text = { Text("激活此课表") },
                                onClick = {
                                    onActivate()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                }
                            )
                        }
                        
                        DropdownMenuItem(
                            text = { Text("课表设置") },
                            onClick = {
                                onSettings()
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Settings, contentDescription = null)
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = {
                                onEdit()
                                expanded = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        
                        if (schedules.size > 1 || !isActive) {
                            DropdownMenuItem(
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    onDelete()
                                    expanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateScheduleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        HyperOSCard(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight()
                .align(Alignment.Center)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "创建新课表",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("课表描述（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name, description) },
                        enabled = name.isNotBlank(),
                        cornerRadius = 24.dp
                    ) {
                        Text("创建")
                    }
                }
            }
        }
    }
}

@Composable
fun EditScheduleDialog(
    schedule: ScheduleInfo,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleInfo) -> Unit
) {
    var name by remember { mutableStateOf(schedule.name) }
    var description by remember { mutableStateOf(schedule.description) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        HyperOSCard(
            modifier = Modifier
                .width(320.dp)
                .wrapContentHeight()
                .align(Alignment.Center)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "编辑课表",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("课表名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("课表描述（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updatedSchedule = schedule.copy(
                                name = name,
                                description = description,
                                updatedAt = System.currentTimeMillis()
                            )
                            onConfirm(updatedSchedule)
                        },
                        enabled = name.isNotBlank(),
                        cornerRadius = 24.dp
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    scheduleName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("确认删除", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Text("确定要删除课表 \"$scheduleName\" 吗？此操作不可撤销。")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonColors(
                    color = MaterialTheme.colorScheme.error,
                    contentColor = Color.White,
                    disabledColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return format.format(date)
}
