package com.example.a8319schedule.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import top.yukonga.miuix.kmp.basic.*

@Composable
fun MyScreen(
    scheduleViewModel: ScheduleViewModel = viewModel(),
    onNavigateToScheduleManagement: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onNavigateToUpdateOtherInfo: () -> Unit = {},
    onNavigateToExamList: () -> Unit = {},
    onNavigateToScore: () -> Unit = {},
    onNavigateToTrainingPlan: () -> Unit = {},
    onNavigateToExport: () -> Unit = {},
    onNavigateToWidgetSettings: () -> Unit = {},
    onNavigateToNotificationSettings: () -> Unit = {},
    onNavigateToAISettings: () -> Unit = {},
    onNavigateToAIChat: () -> Unit = {},
    onNavigateToAIVisionImport: () -> Unit = {},
    onNavigateToShareCode: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.surface
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // 顶部导航栏 - HyperOS 风格
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
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Text(
                    text = "我的",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // 用户信息卡片 - HyperOS 渐变风格
            item {
                HyperOSProfileCard(
                    appName = "江理课程表",
                    version = "v1.1.2",
                    slogan = "好好学习，天天向上",
                    primaryColor = primaryColor
                )
            }
            
            // 功能菜单区标题
            item {
                Text(
                    text = "功能",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            // 课表管理
            item {
                MyMenuItem(
                    title = "课表管理",
                    description = "管理多个课表",
                    iconVector = Icons.Default.CalendarViewWeek,
                    onClick = onNavigateToScheduleManagement,
                    primaryColor = primaryColor
                )
            }
            
            // 导入课表
            item {
                MyMenuItem(
                    title = "导入课表",
                    description = "从教务系统导入新课表",
                    iconVector = Icons.Default.Download,
                    onClick = onNavigateToImport,
                    primaryColor = primaryColor
                )
            }

            // 更新其它信息
            item {
                MyMenuItem(
                    title = "更新其它信息",
                    description = "单独导入考试/成绩/培养方案",
                    iconVector = Icons.Default.Sync,
                    onClick = onNavigateToUpdateOtherInfo,
                    primaryColor = primaryColor
                )
            }
            
            // 考试安排
            item {
                MyMenuItem(
                    title = "考试安排",
                    description = "查看本学期考试安排",
                    iconVector = Icons.Default.Event,
                    onClick = onNavigateToExamList,
                    primaryColor = primaryColor
                )
            }
            
            // 成绩查询
            item {
                MyMenuItem(
                    title = "成绩查询",
                    description = "查看历史成绩和绩点",
                    iconVector = Icons.Default.Grade,
                    onClick = onNavigateToScore,
                    primaryColor = primaryColor
                )
            }
            
            // 培养方案
            item {
                MyMenuItem(
                    title = "培养方案",
                    description = "查看培养方案和执行计划",
                    iconVector = Icons.Default.MenuBook,
                    onClick = onNavigateToTrainingPlan,
                    primaryColor = primaryColor
                )
            }
            
            // 导出课表
            item {
                MyMenuItem(
                    title = "导出课表",
                    description = "导出当前课表数据",
                    iconVector = Icons.Default.Share,
                    onClick = onNavigateToExport,
                    primaryColor = primaryColor
                )
            }
            
            // 分享课表
            item {
                MyMenuItem(
                    title = "分享课表",
                    description = "生成分享码或从分享码导入",
                    iconVector = Icons.Default.QrCode,
                    onClick = onNavigateToShareCode,
                    primaryColor = primaryColor
                )
            }
            
            // 课表设置
            item {
                MyMenuItem(
                    title = "课表设置",
                    description = "设置开学日期，上课时间等",
                    iconVector = Icons.Default.Settings,
                    onClick = onNavigateToSettings,
                    primaryColor = primaryColor
                )
            }
            
            // 小部件设置
            item {
                MyMenuItem(
                    title = "小部件设置",
                    description = "设置桌面小部件",
                    iconVector = Icons.Default.Widgets,
                    onClick = onNavigateToWidgetSettings,
                    primaryColor = primaryColor
                )
            }
            
            // 通知设置
            item {
                MyMenuItem(
                    title = "通知设置",
                    description = "上课提醒和每日课表摘要",
                    iconVector = Icons.Default.Notifications,
                    onClick = onNavigateToNotificationSettings,
                    primaryColor = primaryColor
                )
            }

            // AI 助手区标题
            item {
                Text(
                    text = "AI 助手",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            

            item {
                MyMenuItem(
                    title = "AI助手设置",
                    description = "配置大模型API",
                    iconVector = Icons.Default.AutoAwesome,
                    onClick = onNavigateToAISettings,
                    primaryColor = MaterialTheme.colorScheme.tertiary
                )
            }
            

            item {
                MyMenuItem(
                    title = "AI助手",
                    description = "用AI帮你管理信息",
                    iconVector = Icons.Default.Chat,
                    onClick = onNavigateToAIChat,
                    primaryColor = MaterialTheme.colorScheme.tertiary
                )
            }
            
            // AI导入
            item {
                MyMenuItem(
                    title = "AI导入（敬请期待）",
                    description = "拍照或上传课表图片识别导入",
                    iconVector = Icons.Default.PhotoCamera,
                    onClick = onNavigateToAIVisionImport,
                    primaryColor = MaterialTheme.colorScheme.tertiary
                )
            }
            
            // 关于信息
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "关于",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                HyperOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "关于江理课程表",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "欢迎使用江理课程表，支持从教务系统一键导入课表、培养方案、考试时间、考试成绩，支持分享课表、多课表切换、将课表导出为日历文件、查看成绩可视化图等。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "鸣谢",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "虚拟现实技术 许晨\n智能建造 陶学帅\n智能建造 宁佳俊\n软件工程 李翰深\n智能建造 林惠康\n" +
                                    "虚拟现实技术 张志凯\n软件工程 徐强\n化学工程与工艺 曾庆灵\n智能建造 朱祥云\n(排名不分先后)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "特别鸣谢",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "拾光课程表\n本项目初期曾向拾光课程表借鉴教务系统课表导入经验",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun HyperOSProfileCard(
    appName: String,
    version: String,
    slogan: String,
    primaryColor: Color
) {
    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = primaryColor.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App 图标 - 渐变背景
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                primaryColor,
                                primaryColor.copy(alpha = 0.7f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "App Icon",
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = appName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = slogan,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun MyMenuItem(
    title: String,
    description: String,
    iconVector: ImageVector,
    onClick: () -> Unit,
    primaryColor: Color,
    showArrow: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标容器
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(primaryColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = title,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (showArrow) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "进入",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
        }
    }
}
