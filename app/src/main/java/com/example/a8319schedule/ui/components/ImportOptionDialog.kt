package com.example.a8319schedule.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.data.TimetableParser
import top.yukonga.miuix.kmp.basic.*
import androidx.compose.foundation.shape.CircleShape

@Composable
fun ImportOptionDialog(
    parseResult: TimetableParser.ParseResult,
    schedules: List<ScheduleInfo>,
    activeSchedule: ScheduleInfo?,
    onDismiss: () -> Unit,
    onImportToExisting: (scheduleId: Long) -> Unit,
    onImportToNew: (name: String, description: String) -> Unit,
    onOpenSettings: (scheduleId: Long, scheduleName: String, isNewSchedule: Boolean) -> Unit
) {
    var selectedOption by remember { mutableStateOf("existing") }
    var selectedScheduleId by remember { mutableStateOf(activeSchedule?.id ?: 0L) }
    var newScheduleName by remember { mutableStateOf("") }
    var newScheduleDescription by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    ) {
        HyperOSDialogCard(
            modifier = Modifier
                .width(screenWidth * 0.9f)
                .wrapContentHeight()
                .align(Alignment.Center)
                .clickable(enabled = false) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "导入选项",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "请选择导入方式：",
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 覆盖现有课表选项
                    HyperOSRadioOption(
                        title = "覆盖现有课表",
                        selected = selectedOption == "existing",
                        onClick = { selectedOption = "existing" }
                    )

                    if (selectedOption == "existing") {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            schedules.take(3).forEach { schedule ->
                                FilterChip(
                                    onClick = { selectedScheduleId = schedule.id },
                                    label = { 
                                        Text(
                                            schedule.name, 
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        ) 
                                    },
                                    selected = selectedScheduleId == schedule.id,
                                    modifier = Modifier.weight(1f),
                                    leadingIcon = if (selectedScheduleId == schedule.id) {
                                        {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    // 创建新课表选项
                    HyperOSRadioOption(
                        title = "创建新课表",
                        selected = selectedOption == "new",
                        onClick = { selectedOption = "new" }
                    )

                    if (selectedOption == "new") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newScheduleName,
                            onValueChange = { newScheduleName = it },
                            label = { Text("课表名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newScheduleDescription,
                            onValueChange = { newScheduleDescription = it },
                            label = { Text("课表描述（可选）") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "将要导入 ${parseResult.courses.size} 门课程",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 操作按钮
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
                            if (selectedOption == "existing") {
                                val scheduleName = schedules.find { it.id == selectedScheduleId }?.name ?: "未知课表"
                                onOpenSettings(selectedScheduleId, scheduleName, false)
                            } else {
                                val name = newScheduleName.ifEmpty { "新课表" }
                                onOpenSettings(0L, name, true)
                            }
                        },
                        enabled = if (selectedOption == "existing") {
                            selectedScheduleId > 0
                        } else {
                            newScheduleName.isNotBlank()
                        },
                        cornerRadius = 24.dp
                    ) {
                        Text("下一步")
                    }
                }
            }
        }
    }
}

@Composable
private fun HyperOSRadioOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

// HyperOSDialogCard 已提取到 HyperOSDialogComponents.kt 共享
