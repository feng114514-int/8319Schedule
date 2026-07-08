package com.example.a8319schedule.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.School
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.data.Score
import com.example.a8319schedule.viewmodel.ScoreViewModel
import top.yukonga.miuix.kmp.basic.*
import java.text.DecimalFormat

@Composable
fun ScoreListScreen(
    onNavigateBack: () -> Unit,
    scoreViewModel: ScoreViewModel = viewModel()
) {
    val scores by scoreViewModel.scores.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        // 顶部导航栏
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
                    text = "成绩查询",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (scores.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无成绩数据",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "导入课表时会自动获取成绩",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = primaryColor
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("成绩列表") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("成绩可视化") }
                )
            }

            when (selectedTab) {
                0 -> ScoreListTab(scores = scores, primaryColor = primaryColor)
                1 -> ScoreVisualizationTab(scores = scores, primaryColor = primaryColor)
            }
        }
    }
}

@Composable
private fun ScoreListTab(scores: List<Score>, primaryColor: Color) {
    // 已获得学分：仅统计成绩≥60的课程
    val totalCredit = scores
        .filter { it.score.toDoubleOrNull()?.let { v -> v >= 60 } == true }
        .mapNotNull { it.credit.toDoubleOrNull() }.sum()
    val weightedGpa = run {
        val totalWeight = scores.mapNotNull { it.credit.toDoubleOrNull() }.sum()
        val totalPoint = scores.mapNotNull { s ->
            s.gradePoint.toDoubleOrNull()?.let { gp ->
                s.credit.toDoubleOrNull()?.let { c -> gp * c }
            }
        }.sum()
        if (totalWeight > 0) totalPoint / totalWeight else 0.0
    }
    val passCount = scores.count { it.score.toDoubleOrNull()?.let { v -> v >= 60 } == true }
    val failCount = scores.count { it.score.toDoubleOrNull()?.let { v -> v < 60 } == true }

    val groupedBySemester = scores.groupBy { it.semester.ifBlank { "未知学期" } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            ScoreSummaryCard(
                totalCount = scores.size,
                totalCredit = totalCredit,
                avgGpa = weightedGpa,
                passCount = passCount,
                failCount = failCount,
                primaryColor = primaryColor
            )
        }

        groupedBySemester.forEach { (semester, semesterScores) ->
            item {
                SemesterSectionHeader(
                    semester = semester,
                    courseCount = semesterScores.size,
                    semesterCredit = semesterScores.mapNotNull { it.credit.toDoubleOrNull() }.sum(),
                    semesterGpa = run {
                        val tw = semesterScores.mapNotNull { it.credit.toDoubleOrNull() }.sum()
                        val tp = semesterScores.mapNotNull { s ->
                            s.gradePoint.toDoubleOrNull()?.let { gp ->
                                s.credit.toDoubleOrNull()?.let { c -> gp * c }
                            }
                        }.sum()
                        if (tw > 0) tp / tw else 0.0
                    }
                )
            }
            items(semesterScores, key = { it.id }) { score ->
                ScoreCard(score = score, primaryColor = primaryColor)
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ScoreSummaryCard(
    totalCount: Int,
    totalCredit: Double,
    avgGpa: Double,
    passCount: Int,
    failCount: Int,
    primaryColor: Color
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val df = DecimalFormat("0.00")

    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = primaryColor.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(primaryColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Assessment,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "成绩概览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 四宫格统计
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryStatItem(
                    label = "课程数",
                    value = totalCount.toString(),
                    modifier = Modifier.weight(1f),
                    valueColor = onSurface
                )
                SummaryStatItem(
                    label = "已获学分",
                    value = df.format(totalCredit),
                    modifier = Modifier.weight(1f),
                    valueColor = onSurface
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryStatItem(
                    label = "平均绩点",
                    value = df.format(avgGpa),
                    modifier = Modifier.weight(1f),
                    valueColor = primaryColor
                )
                SummaryStatItem(
                    label = "及格/不及格",
                    value = "$passCount / $failCount",
                    modifier = Modifier.weight(1f),
                    valueColor = onSurface
                )
            }
        }
    }
}

@Composable
private fun SummaryStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SemesterSectionHeader(
    semester: String,
    courseCount: Int,
    semesterCredit: Double,
    semesterGpa: Double
) {
    val df = DecimalFormat("0.00")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = semester,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "$courseCount 门",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "学分 ${df.format(semesterCredit)}  绩点 ${df.format(semesterGpa)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreCard(score: Score, primaryColor: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    // 成绩数值与颜色
    val scoreValue = score.score.toDoubleOrNull()
    val isPass = scoreValue != null && scoreValue >= 60
    val scoreColor = when {
        scoreValue == null -> onSurfaceVariant
        scoreValue >= 85 -> Color(0xFF2E7D32)  // 优秀 绿
        scoreValue >= 60 -> primaryColor       // 及格 主题色
        else -> Color(0xFFD32F2F)              // 不及格 红
    }

    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 课程信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = score.courseName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface
                        )
                        Text(
                            text = score.courseCode,
                            fontSize = 11.sp,
                            color = onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 标签行：学分 / 绩点 / 考核方式
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (score.credit.isNotBlank()) {
                        ScoreTag(text = "${score.credit}学分", color = primaryColor)
                    }
                    if (score.gradePoint.isNotBlank()) {
                        ScoreTag(text = "绩点${score.gradePoint}", color = primaryColor)
                    }
                    if (score.assessmentType.isNotBlank()) {
                        ScoreTag(text = score.assessmentType, color = onSurfaceVariant)
                    }
                    if (score.courseAttribute.isNotBlank()) {
                        ScoreTag(text = score.courseAttribute, color = onSurfaceVariant)
                    }
                }
            }

            // 成绩大字
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = score.score.ifBlank { "-" },
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
                if (score.totalHours.isNotBlank()) {
                    Text(
                        text = "${score.totalHours}学时",
                        fontSize = 11.sp,
                        color = onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreTag(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ==================== 成绩可视化 Tab ====================

@Composable
private fun ScoreVisualizationTab(scores: List<Score>, primaryColor: Color) {
    // 各学期加权绩点
    val semesterGpaData = scores
        .groupBy { it.semester.ifBlank { "未知学期" } }
        .mapNotNull { (semester, list) ->
            val valid = list.filter {
                it.gradePoint.toDoubleOrNull() != null && it.credit.toDoubleOrNull() != null
            }
            if (valid.isEmpty()) return@mapNotNull null
            val tw = valid.mapNotNull { it.credit.toDoubleOrNull() }.sum()
            val tp = valid.mapNotNull {
                it.gradePoint.toDoubleOrNull()!! * it.credit.toDoubleOrNull()!!
            }.sum()
            SemesterGpaPoint(semester, if (tw > 0) tp / tw else 0.0)
        }
        .sortedBy { it.semester }

    // 成绩分布
    val distribution = listOf(
        ScoreBucket("优秀", scores.count { it.score.toDoubleOrNull()?.let { v -> v >= 85 } == true }, Color(0xFF2E7D32)),
        ScoreBucket("良好", scores.count { it.score.toDoubleOrNull()?.let { v -> v >= 75 && v < 85 } == true }, primaryColor),
        ScoreBucket("及格", scores.count { it.score.toDoubleOrNull()?.let { v -> v >= 60 && v < 75 } == true }, primaryColor.copy(alpha = 0.5f)),
        ScoreBucket("不及格", scores.count { it.score.toDoubleOrNull()?.let { v -> v < 60 } == true }, Color(0xFFD32F2F))
    )

    // 各学期已获得学分（仅≥60）
    val semesterCreditData = scores
        .filter { it.score.toDoubleOrNull()?.let { v -> v >= 60 } == true }
        .groupBy { it.semester.ifBlank { "未知学期" } }
        .map { (semester, list) ->
            SemesterCredit(semester, list.mapNotNull { it.credit.toDoubleOrNull() }.sum())
        }
        .sortedBy { it.semester }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            VisualizationOverviewCard(scores = scores, primaryColor = primaryColor)
        }
        item {
            ChartCard(title = "各学期绩点趋势", subtitle = "按学分加权平均") {
                GpaTrendChart(data = semesterGpaData, primaryColor = primaryColor)
            }
        }
        item {
            ChartCard(title = "成绩分布", subtitle = "按分数段统计课程数") {
                ScoreDistributionChart(buckets = distribution)
            }
        }
        item {
            ChartCard(title = "各学期已获得学分", subtitle = "仅统计成绩≥60的课程") {
                CreditBySemesterChart(data = semesterCreditData, primaryColor = primaryColor)
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun VisualizationOverviewCard(scores: List<Score>, primaryColor: Color) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val df = DecimalFormat("0.00")

    val earnedCredit = scores
        .filter { it.score.toDoubleOrNull()?.let { v -> v >= 60 } == true }
        .mapNotNull { it.credit.toDoubleOrNull() }.sum()
    val weightedGpa = run {
        val tw = scores.mapNotNull { it.credit.toDoubleOrNull() }.sum()
        val tp = scores.mapNotNull { s ->
            s.gradePoint.toDoubleOrNull()?.let { gp ->
                s.credit.toDoubleOrNull()?.let { c -> gp * c }
            }
        }.sum()
        if (tw > 0) tp / tw else 0.0
    }
    val passRate = if (scores.isNotEmpty()) {
        scores.count { it.score.toDoubleOrNull()?.let { v -> v >= 60 } == true }.toDouble() / scores.size * 100
    } else 0.0

    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = primaryColor.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BigStatItem(
                value = df.format(earnedCredit),
                label = "已获学分",
                color = onSurface,
                modifier = Modifier.weight(1f)
            )
            BigStatItem(
                value = df.format(weightedGpa),
                label = "平均绩点",
                color = primaryColor,
                modifier = Modifier.weight(1f)
            )
            BigStatItem(
                value = "${df.format(passRate)}%",
                label = "及格率",
                color = onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BigStatItem(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ChartCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    HyperOSCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
            }
            content()
        }
    }
}

// ---------- 图1：各学期绩点折线图 ----------

@Composable
private fun GpaTrendChart(data: List<SemesterGpaPoint>, primaryColor: Color) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = onSurfaceVariant.copy(alpha = 0.12f)
    val df = DecimalFormat("0.00")

    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无有效绩点数据", fontSize = 13.sp, color = onSurfaceVariant)
        }
        return
    }

    val maxGpa = maxOf(data.maxOf { it.gpa } + 0.5, 1.0)
    val ySteps = 4

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val leftPad = 42.dp.toPx()
        val rightPad = 16.dp.toPx()
        val topPad = 18.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val stepX = if (data.size > 1) chartW / (data.size - 1) else chartW

        val labelPaint = android.graphics.Paint().apply {
            color = onSurfaceVariant.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        val needRotate = data.any { labelPaint.measureText(it.semester) > stepX * 0.9f }
        val bottomPad = (if (needRotate) 60 else 36).dp.toPx()
        val chartH = size.height - topPad - bottomPad

        val valuePaint = android.graphics.Paint().apply {
            color = primaryColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // Y轴网格线 + 标签
        for (i in 0..ySteps) {
            val y = topPad + chartH * (1f - i.toFloat() / ySteps)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx()
            )
            val v = maxGpa * i / ySteps
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            drawIntoCanvas {
                it.nativeCanvas.drawText(df.format(v), leftPad - 6.dp.toPx(), y + 4.dp.toPx(), labelPaint)
            }
        }

        if (data.size == 1) {
            val p = data[0]
            val x = leftPad + chartW / 2
            val y = topPad + chartH * (1f - (p.gpa / maxGpa).toFloat())
            drawCircle(primaryColor, radius = 7.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(x, y))
            drawIntoCanvas {
                it.nativeCanvas.drawText(df.format(p.gpa), x, y - 12.dp.toPx(), valuePaint)
            }
            val labelY = size.height - 8.dp.toPx()
            val textW = labelPaint.measureText(p.semester)
            drawIntoCanvas {
                if (textW > stepX * 0.9f) {
                    it.nativeCanvas.save()
                    it.nativeCanvas.rotate(-45f, x, labelY)
                    labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                    it.nativeCanvas.drawText(p.semester, x, labelY, labelPaint)
                    it.nativeCanvas.restore()
                } else {
                    labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                    it.nativeCanvas.drawText(p.semester, x, labelY, labelPaint)
                }
            }
        } else {
            val stepX = chartW / (data.size - 1)
            // 折线
            val path = Path()
            data.forEachIndexed { i, p ->
                val x = leftPad + stepX * i
                val y = topPad + chartH * (1f - (p.gpa / maxGpa).toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = primaryColor, style = Stroke(width = 2.5.dp.toPx()))
            // 数据点 + 标签
            data.forEachIndexed { i, p ->
                val x = leftPad + stepX * i
                val y = topPad + chartH * (1f - (p.gpa / maxGpa).toFloat())
                drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(x, y))
                drawCircle(primaryColor, radius = 7.dp.toPx(), center = Offset(x, y), style = Stroke(width = 2.5.dp.toPx()))
                drawIntoCanvas {
                    it.nativeCanvas.drawText(df.format(p.gpa), x, y - 14.dp.toPx(), valuePaint)
                }
                val labelY = size.height - 8.dp.toPx()
                val textW = labelPaint.measureText(p.semester)
                drawIntoCanvas {
                    if (textW > stepX * 0.9f) {
                        it.nativeCanvas.save()
                        it.nativeCanvas.rotate(-45f, x, labelY)
                        labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                        it.nativeCanvas.drawText(p.semester, x, labelY, labelPaint)
                        it.nativeCanvas.restore()
                    } else {
                        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                        it.nativeCanvas.drawText(p.semester, x, labelY, labelPaint)
                    }
                }
            }
        }
    }
}

// ---------- 图2：成绩分布柱状图 ----------

@Composable
private fun ScoreDistributionChart(buckets: List<ScoreBucket>) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val maxCount = buckets.maxOf { it.count }.coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val leftPad = 16.dp.toPx()
        val rightPad = 16.dp.toPx()
        val topPad = 24.dp.toPx()
        val bottomPad = 36.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad
        val slotW = chartW / buckets.size
        val barW = slotW * 0.55f

        val countPaint = android.graphics.Paint().apply {
            color = onSurfaceVariant.toArgb()
            textSize = 11.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val labelPaint = android.graphics.Paint().apply {
            color = onSurfaceVariant.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        buckets.forEachIndexed { i, bucket ->
            val slotCenter = leftPad + slotW * (i + 0.5f)
            val barLeft = slotCenter - barW / 2
            val barH = chartH * (bucket.count.toFloat() / maxCount)
            val barTop = topPad + chartH - barH

            drawRoundRect(
                color = bucket.color,
                topLeft = Offset(barLeft, barTop),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawIntoCanvas {
                it.nativeCanvas.drawText(bucket.count.toString(), slotCenter, barTop - 6.dp.toPx(), countPaint)
            }
            drawIntoCanvas {
                it.nativeCanvas.drawText(bucket.label, slotCenter, size.height - 8.dp.toPx(), labelPaint)
            }
        }
    }
}

// ---------- 图3：各学期已获得学分柱状图 ----------

@Composable
private fun CreditBySemesterChart(data: List<SemesterCredit>, primaryColor: Color) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = onSurfaceVariant.copy(alpha = 0.12f)
    val df = DecimalFormat("0.0")

    if (data.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无已获得学分数据", fontSize = 13.sp, color = onSurfaceVariant)
        }
        return
    }

    val maxCredit = maxOf(data.maxOf { it.credit } * 1.15, 1.0)
    val ySteps = 4

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        val leftPad = 42.dp.toPx()
        val rightPad = 16.dp.toPx()
        val topPad = 24.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val slotW = chartW / data.size
        val barW = slotW * 0.5f

        val labelPaint = android.graphics.Paint().apply {
            color = onSurfaceVariant.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
        }
        val needRotate = data.any { labelPaint.measureText(it.semester) > slotW * 0.9f }
        val bottomPad = (if (needRotate) 60 else 36).dp.toPx()
        val chartH = size.height - topPad - bottomPad
        val valuePaint = android.graphics.Paint().apply {
            color = primaryColor.toArgb()
            textSize = 10.sp.toPx()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }

        // Y轴网格线
        for (i in 0..ySteps) {
            val y = topPad + chartH * (1f - i.toFloat() / ySteps)
            drawLine(
                color = gridColor,
                start = Offset(leftPad, y),
                end = Offset(size.width - rightPad, y),
                strokeWidth = 1.dp.toPx()
            )
            val v = maxCredit * i / ySteps
            labelPaint.textAlign = android.graphics.Paint.Align.RIGHT
            drawIntoCanvas {
                it.nativeCanvas.drawText(df.format(v), leftPad - 6.dp.toPx(), y + 4.dp.toPx(), labelPaint)
            }
        }

        // 柱子
        data.forEachIndexed { i, point ->
            val slotCenter = leftPad + slotW * (i + 0.5f)
            val barLeft = slotCenter - barW / 2
            val barH = chartH * (point.credit.toFloat() / maxCredit.toFloat())
            val barTop = topPad + chartH - barH
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(barLeft, barTop),
                size = Size(barW, barH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawIntoCanvas {
                it.nativeCanvas.drawText(df.format(point.credit), slotCenter, barTop - 6.dp.toPx(), valuePaint)
            }
            val labelY = size.height - 8.dp.toPx()
            val textW = labelPaint.measureText(point.semester)
            drawIntoCanvas {
                if (textW > slotW * 0.9f) {
                    it.nativeCanvas.save()
                    it.nativeCanvas.rotate(-45f, slotCenter, labelY)
                    labelPaint.textAlign = android.graphics.Paint.Align.LEFT
                    it.nativeCanvas.drawText(point.semester, slotCenter, labelY, labelPaint)
                    it.nativeCanvas.restore()
                } else {
                    labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                    it.nativeCanvas.drawText(point.semester, slotCenter, labelY, labelPaint)
                }
            }
        }
    }
}

// ==================== 数据类 ====================

private data class SemesterGpaPoint(val semester: String, val gpa: Double)
private data class ScoreBucket(val label: String, val count: Int, val color: Color)
private data class SemesterCredit(val semester: String, val credit: Double)
