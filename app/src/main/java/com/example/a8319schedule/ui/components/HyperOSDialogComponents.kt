package com.example.a8319schedule.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import top.yukonga.miuix.kmp.basic.*

/**
 * HyperOS 风格共享组件
 * 整合了 CourseDetailDialog 和 ImportOptionDialog 中重复定义的组件
 */

/**
 * HyperOS 风格对话框卡片
 */
@Composable
fun HyperOSDialogCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        cornerRadius = 24.dp,
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(content = content)
    }
}

/**
 * HyperOS 风格对话框按钮
 */
@Composable
fun HyperOSDialogButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonColors(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
    ),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * HyperOS 风格弹窗缓动曲线
 */
object HyperOSEasing {
    val DialogEnter = CubicBezierEasing(0.15f, 0.1f, 0.25f, 1f)
    val DialogExit = CubicBezierEasing(0.4f, 0f, 1f, 1f)
}

/**
 * HyperOS 风格通用对话框
 * 替代 Material3 AlertDialog，深色/白天视觉统一
 *
 * 基于 Compose [Dialog] 实现真正的浮层（独立 window），
 * 不占布局空间，无论调用环境（LazyColumn item / Column / Box）都能正常浮在最上层。
 *
 * @param visible 是否可见
 * @param onDismissRequest 点击遮罩/返回键时的回调
 * @param title 标题文本
 * @param confirmText 确认按钮文本
 * @param onConfirm 确认回调（不自动关闭，调用方需自行将 visible 置 false）
 * @param dismissText 取消按钮文本，传 null 则不显示取消按钮
 * @param content 对话框主体内容
 */
@Composable
fun HyperOSAlertDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String = "确定",
    onConfirm: () -> Unit,
    dismissText: String? = "取消",
    content: @Composable ColumnScope.() -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true
            )
        ) {
            val configuration = LocalConfiguration.current
            HyperOSDialogCard(
                modifier = Modifier.width(configuration.screenWidthDp.dp * 0.85f)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (dismissText != null) {
                            HyperOSDialogButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.weight(1f),
                                colors = ButtonColors(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(dismissText)
                            }
                        }
                        HyperOSDialogButton(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(confirmText)
                        }
                    }
                }
            }
        }
    }
}
