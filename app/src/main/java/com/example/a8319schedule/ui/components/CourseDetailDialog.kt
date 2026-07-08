package com.example.a8319schedule.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.data.CourseScheduleTimes
import top.yukonga.miuix.kmp.basic.*
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun CourseDetailDialog(
    course: Course?,
    weekNumber: Int,
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AnimatedVisibility(
        visible = isVisible && course != null,
        enter = fadeIn(animationSpec = tween(250)) + scaleIn(
            initialScale = 0.85f,
            animationSpec = tween(350, easing = HyperOSEasing.DialogEnter)
        ),
        exit = fadeOut(animationSpec = tween(200)) + scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(250, easing = HyperOSEasing.DialogExit)
        )
    ) {
        if (course == null) return@AnimatedVisibility
        
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
                    .width(screenWidth * 0.85f)
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
                            text = "课程详情",
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
                    
                    // 课程信息
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = course.name,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        
                        if (course.teacher.isNotBlank()) {
                            InfoRow(label = "教师", value = course.teacher)
                        }
                        
                        // 使用 CourseScheduleTimes 获取时间范围文本
                        val days = listOf("", "周一", "周二", "周三", "周四", "周五", "周六", "周日")
                        InfoRow(
                            label = "时间",
                            value = "${days[course.dayOfWeek]} ${CourseScheduleTimes.getTimeRangeText(course.startPeriod, course.endPeriod)}"
                        )
                        
                        if (course.classroom.isNotBlank()) {
                            InfoRow(label = "地点", value = course.classroom)
                        }
                        
                        InfoRow(label = "教学周", value = "第${course.weekNumber}周")
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 操作按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HyperOSDialogButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f),
                            colors = ButtonColors(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error,
                                disabledColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("删除")
                        }
                        
                        HyperOSDialogButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f),
                            colors = ButtonColors(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("编辑")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label：",
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
