package com.example.a8319schedule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * QQ 聊天记录式左滑操作容器
 *
 * 左滑露出右侧两个按钮（灰色编辑 + 红色删除），各占一半宽度，共 160dp。
 * - 松手后吸附到 0（关闭）或 -actionWidth（打开）
 * - 仅消费水平拖拽，不影响父级 LazyColumn 的垂直滚动
 * - 点击按钮后自动复位
 *
 * @param onEdit 点击编辑按钮
 * @param onDelete 点击删除按钮
 * @param content 上层内容（通常是一张卡片）
 */
@Composable
fun SwipeToActionBox(
    modifier: Modifier = Modifier,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val actionWidth = 160.dp
    val density = LocalDensity.current
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    val dragState = rememberDraggableState { delta ->
        scope.launch {
            val target = (offsetX.value + delta).coerceIn(-actionWidthPx, 0f)
            offsetX.snapTo(target)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // 底层：右侧两个操作按钮，右对齐并填满高度
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(actionWidth)
        ) {
            // 编辑按钮 - 灰色，左半
            ActionButton(
                backgroundColor = Color(0xFF9E9E9E),
                icon = Icons.Default.Edit,
                label = "编辑",
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch { offsetX.animateTo(0f, tween(150)) }
                    onEdit()
                }
            )
            // 删除按钮 - 红色，右半
            ActionButton(
                backgroundColor = Color(0xFFFF5252),
                icon = Icons.Default.Delete,
                label = "删除",
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch { offsetX.animateTo(0f, tween(150)) }
                    onDelete()
                }
            )
        }

        // 上层：内容卡片，随拖拽水平偏移
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.toInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = {
                        scope.launch {
                            val target = if (offsetX.value < -actionWidthPx / 2f) -actionWidthPx else 0f
                            offsetX.animateTo(target, tween(200))
                        }
                    }
                )
        ) {
            content()
        }
    }
}

@Composable
private fun ActionButton(
    backgroundColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
