package com.example.a8319schedule.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.data.TrainingPlan
import com.example.a8319schedule.data.TrainingPlanAll
import com.example.a8319schedule.viewmodel.TrainingPlanViewModel
import top.yukonga.miuix.kmp.basic.*

@Composable
fun TrainingPlanScreen(
    onNavigateBack: () -> Unit,
    trainingPlanViewModel: TrainingPlanViewModel = viewModel()
) {
    val plans by trainingPlanViewModel.trainingPlans.collectAsState()
    val allPlans by trainingPlanViewModel.allTrainingPlans.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background

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
                    text = "培养方案",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 二级菜单 Tab 切换：执行计划 / 所有培养方案
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = primaryColor
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("执行计划") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("所有培养方案") }
            )
        }

        when (selectedTab) {
            0 -> ExecutionPlanContent(plans = plans, primaryColor = primaryColor)
            1 -> AllPlansContent(allPlans = allPlans, primaryColor = primaryColor)
        }
    }
}

@Composable
private fun ExecutionPlanContent(plans: List<TrainingPlan>, primaryColor: Color) {
    if (plans.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无培养方案",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "导入课表时会自动获取培养方案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    // 按学期分组
    val grouped = plans.groupBy { it.semester }
    val totalCourses = plans.size
    val totalCredits = plans.mapNotNull { it.credit.toDoubleOrNull() }.sum()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 统计卡片
        item {
            HyperOSCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = primaryColor.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = "$totalCourses", label = "课程", primaryColor = primaryColor)
                    StatItem(value = formatCredit(totalCredits), label = "总学分", primaryColor = primaryColor)
                    StatItem(value = "${grouped.size}", label = "学期", primaryColor = primaryColor)
                }
            }
        }

        // 按学期分组展示
        grouped.forEach { (semester, semesterPlans) ->
            item {
                SemesterHeader(semester = semester, count = semesterPlans.size, primaryColor = primaryColor)
            }
            items(semesterPlans, key = { it.id }) { plan ->
                PlanCard(plan = plan, primaryColor = primaryColor)
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun StatItem(value: String, label: String, primaryColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SemesterHeader(semester: String, count: Int, primaryColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = semester,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "（$count 门）",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanCard(plan: TrainingPlan, primaryColor: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = plan.courseCode,
                        fontSize = 12.sp,
                        color = onSurfaceVariant
                    )
                }
                // 学分标签
                Surface(
                    color = primaryColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "${plan.credit} 学分",
                        fontSize = 12.sp,
                        color = primaryColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 信息行：总学时 / 考核方式 / 课程性质
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlanTag(text = "总学时 ${plan.totalHours}")
                PlanTag(text = plan.assessmentType)
                PlanTag(text = plan.courseNature)
            }
        }
    }
}

@Composable
private fun PlanTag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ===================== 所有培养方案（课程设置总表） =====================

/**
 * 所有培养方案内容：支持按课程体系 / 按开课学期分组，并可按必修/选修筛选。
 */
@Composable
private fun AllPlansContent(allPlans: List<TrainingPlanAll>, primaryColor: Color) {
    if (allPlans.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无课程设置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "导入课表时会自动获取培养方案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        return
    }

    // 分组方式：system=按课程体系 / semester=按开课学期
    var groupMode by remember { mutableStateOf("system") }
    // 筛选：all=全部 / required=必修 / optional=选修
    var filter by remember { mutableStateOf("all") }

    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 先按属性筛选（课程属性列：必修 / 任选 / 限选 等）
    val filtered = allPlans.filter { plan ->
        when (filter) {
            "required" -> plan.courseAttribute.trim() == "必修"
            "optional" -> plan.courseAttribute.trim() != "必修"
            else -> true
        }
    }

    // 统计
    val totalCourses = filtered.size
    val totalCredits = filtered.mapNotNull { it.credit.toDoubleOrNull() }.sum()
    val requiredCount = filtered.count { it.courseAttribute.trim() == "必修" }
    val optionalCount = filtered.count { it.courseAttribute.trim() != "必修" }

    // 再按选定维度分组
    val grouped: List<Pair<String, List<TrainingPlanAll>>> = when (groupMode) {
        "semester" -> filtered
            .groupBy { it.openSemester.ifBlank { "未安排" } }
            .toSortedMap(compareBy<String> { it.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it })
            .map { it.key to it.value }
        else -> filtered
            .groupBy { it.courseSystem.ifBlank { "未分类" } }
            .map { it.key to it.value }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 统计卡片
        item {
            HyperOSCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = primaryColor.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(value = "$totalCourses", label = "课程", primaryColor = primaryColor)
                    StatItem(value = formatCredit(totalCredits), label = "总学分", primaryColor = primaryColor)
                    StatItem(value = "$requiredCount", label = "必修", primaryColor = primaryColor)
                    StatItem(value = "$optionalCount", label = "选修", primaryColor = primaryColor)
                }
            }
        }

        // 分组方式切换
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SegmentOption(
                    text = "按课程体系",
                    selected = groupMode == "system",
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                ) { groupMode = "system" }
                SegmentOption(
                    text = "按开课学期",
                    selected = groupMode == "semester",
                    primaryColor = primaryColor,
                    modifier = Modifier.weight(1f)
                ) { groupMode = "semester" }
            }
        }

        // 筛选
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterOption(text = "全部", selected = filter == "all", primaryColor = primaryColor) { filter = "all" }
                FilterOption(text = "必修", selected = filter == "required", primaryColor = primaryColor) { filter = "required" }
                FilterOption(text = "选修", selected = filter == "optional", primaryColor = primaryColor) { filter = "optional" }
            }
        }

        if (grouped.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "当前筛选下无课程",
                        color = onSurfaceVariant
                    )
                }
            }
        } else {
            grouped.forEach { (title, list) ->
                item { AllPlanGroupHeader(title = title, plans = list, groupMode = groupMode, primaryColor = primaryColor) }
                items(list, key = { it.id }) { plan ->
                    AllPlanCard(plan = plan, primaryColor = primaryColor)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun SegmentOption(
    text: String,
    selected: Boolean,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = if (selected) primaryColor else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun FilterOption(
    text: String,
    selected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = if (selected) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun AllPlanGroupHeader(
    title: String,
    plans: List<TrainingPlanAll>,
    groupMode: String,
    primaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(primaryColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val displayTitle = if (groupMode == "semester") {
            val num = title.toIntOrNull()
            if (num != null) "第${num}学期" else title
        } else {
            title
        }
        Text(
            text = displayTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        val credits = plans.mapNotNull { it.credit.toDoubleOrNull() }.sum()
        Text(
            text = "（${plans.size} 门 · ${formatCredit(credits)} 学分）",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllPlanCard(plan: TrainingPlanAll, primaryColor: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 完成情况列：已修课程填入"已修（分数）"，未修为空
    val score = plan.completionStatus.trim()
    val completed = score.isNotEmpty()
    val doneColor = Color(0xFF34A853) // 绿色，表示已修完

    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (completed) doneColor.copy(alpha = 0.12f) else primaryColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (completed) Icons.Default.CheckCircle else Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = if (completed) doneColor else primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = plan.courseCode,
                        fontSize = 12.sp,
                        color = onSurfaceVariant
                    )
                }
                // 已修完显示绿色"已修（分数）"标签，未修显示学分标签
                Surface(
                    color = if (completed) doneColor.copy(alpha = 0.12f) else primaryColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = if (completed) "$score，${plan.credit.trim()} 学分" else "${plan.credit.trim()} 学分",
                        fontSize = 12.sp,
                        color = if (completed) doneColor else primaryColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 信息行：总学时 / 课程性质 / 课程属性 / 开设学期
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlanTag(text = "总学时 ${plan.totalHours}")
                if (plan.courseNature.isNotBlank()) PlanTag(text = plan.courseNature)
                if (plan.courseAttribute.isNotBlank()) PlanTag(text = plan.courseAttribute)
                val sem = plan.openSemester.trim()
                if (sem.isNotEmpty()) {
                    val num = sem.toIntOrNull()
                    PlanTag(text = if (num != null) "第${num}学期" else "学期 $sem")
                }
            }
        }
    }
}

private fun formatCredit(credit: Double): String {
    return if (credit % 1.0 == 0.0) credit.toInt().toString()
    else String.format("%.1f", credit)
}
