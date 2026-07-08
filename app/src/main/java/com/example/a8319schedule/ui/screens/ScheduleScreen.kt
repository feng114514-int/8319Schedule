package com.example.a8319schedule.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.data.ScheduleInfo
import com.example.a8319schedule.data.ScheduleSettingsManager
import com.example.a8319schedule.data.PeriodTime
import com.example.a8319schedule.ui.components.CourseCard
import com.example.a8319schedule.viewmodel.CourseViewModel
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import com.example.a8319schedule.data.DailyQuote
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.gestures.detectTapGestures

fun calculateWeekDates(weekNumber: Int, semesterStartDate: Long? = null): List<String> {
    val dateFormat = SimpleDateFormat("M/d", Locale.getDefault())
    val weekDates = mutableListOf<String>()
    
    val semesterStart = Calendar.getInstance().apply {
        if (semesterStartDate != null) {
            timeInMillis = semesterStartDate
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        } else {
            val currentCalendar = Calendar.getInstance()
            val currentYear = currentCalendar.get(Calendar.YEAR)
            val currentMonth = currentCalendar.get(Calendar.MONTH)
            
            val semesterYear = if (currentMonth < Calendar.SEPTEMBER) currentYear - 1 else currentYear
            
            set(Calendar.YEAR, semesterYear)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 1)
            
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }
    }
    
    val weekStart = Calendar.getInstance().apply {
        timeInMillis = semesterStart.timeInMillis
        add(Calendar.DAY_OF_MONTH, (weekNumber - 1) * 7)
    }
    
    for (i in 0..6) {
        val day = Calendar.getInstance().apply {
            timeInMillis = weekStart.timeInMillis
            add(Calendar.DAY_OF_MONTH, i)
        }
        weekDates.add(dateFormat.format(day.time))
    }
    
    return weekDates
}

fun getChineseNumber(num: Int): String {
    return when (num) {
        1 -> "一"
        2 -> "二"
        3 -> "三"
        4 -> "四"
        5 -> "五"
        6 -> "六"
        7 -> "七"
        8 -> "八"
        9 -> "九"
        10 -> "十"
        11 -> "十一"
        12 -> "十二"
        else -> num.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    key: String = "schedule_screen",
    viewModel: CourseViewModel,
    scheduleViewModel: ScheduleViewModel,
    isColdStart: Boolean = true,
    onColdStartHandled: () -> Unit = {},
    onAddCourse: () -> Unit,
    onEditCourse: (Long, Int) -> Unit,
    onFullEditCourse: (Long) -> Unit = {}
) {
    val courses by viewModel.allCourses.collectAsState(initial = emptyList())
    val currentWeek by viewModel.currentWeek.collectAsState()
    val activeSchedule by scheduleViewModel.activeSchedule.collectAsState()
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        viewModel.generateMissingCourseGroupIds()
    }

    val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val periods = 5
    val periodTimes = ScheduleSettingsManager.DEFAULT_TIMES
    
    val currentDate = remember(currentWeek) {
        val calendar = Calendar.getInstance()
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            Calendar.SUNDAY -> "周日"
            else -> "周一"
        }
        Triple(month, day, dayOfWeek)
    }

    var showWeekPicker by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val dailyQuote = remember { DailyQuote.getDailyQuote(context) }
    var showQuote by remember { mutableStateOf(true) }
    var quoteVisible by remember { mutableStateOf(false) }
    
    // 5秒后自动消失
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300) // 等布局完成后开始动画
        quoteVisible = true
        kotlinx.coroutines.delay(5000)
        quoteVisible = false
        kotlinx.coroutines.delay(500) // 等消失动画完成
        showQuote = false
    }
    
    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceIn(0, 19),
        pageCount = { 20 }
    )
    
    var hasInitialSet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (isColdStart) {
            onColdStartHandled()
        }
        hasInitialSet = true
    }

    LaunchedEffect(currentWeek) {
        if (currentWeek > 0 && hasInitialSet && pagerState.currentPage != (currentWeek - 1).coerceIn(0, 19)) {
            val targetPage = (currentWeek - 1).coerceIn(0, 19)
            scope.launch {
                if (!pagerState.isScrollInProgress) {
                    pagerState.scrollToPage(targetPage)
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (activeSchedule != null && !pagerState.isScrollInProgress && hasInitialSet) {
            kotlinx.coroutines.delay(50)
            val newWeek = pagerState.currentPage + 1
            if (currentWeek != newWeek) {
                viewModel.setCurrentWeek(newWeek)
            }
        }
    }

    Scaffold(
        topBar = {
            Box {
                HyperOSTopAppBar(
                    title = "${currentDate.first}月${currentDate.second}日 ${currentDate.third}",
                    actions = {
                        IconButton(onClick = onAddCourse) {
                            Icon(Icons.Default.Add, contentDescription = "添加课程")
                        }
                    }
                )
                // 每日诗句浮层卡片（覆盖在标题栏上方，下滑淡入/淡出上滑动画）
                if (showQuote) {
                    val animSpec = tween<Float>(durationMillis = 500)
                    val slideProgress by animateFloatAsState(
                        targetValue = if (quoteVisible) 1f else 0f,
                        animationSpec = animSpec
                    )
                    val cardAlpha by animateFloatAsState(
                        targetValue = if (quoteVisible) 1f else 0f,
                        animationSpec = animSpec
                    )
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                translationY = -size.height * (1f - slideProgress)
                                alpha = cardAlpha
                            },
                        shape = RoundedCornerShape(0.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dailyQuote,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2
                            )
                            IconButton(
                                onClick = {
                                    quoteVisible = false
                                    scope.launch {
                                        kotlinx.coroutines.delay(500)
                                        showQuote = false
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "关闭",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val weekNumber = page + 1
                val semesterStartDate = activeSchedule?.let { schedule ->
                    if (schedule.startDate > 0) {
                        schedule.startDate
                    } else null
                }
                
                val weekDates = remember(weekNumber, semesterStartDate) {
                    calculateWeekDates(weekNumber, semesterStartDate)
                }
                
                Column(modifier = Modifier.fillMaxSize()) {
                    WeekHeaderRow(
                        currentWeek = weekNumber,
                        weekDays = weekDays,
                        weekDates = weekDates,
                        onWeekClick = { showWeekPicker = true }
                    )
                    
                    CourseGrid(
                        courses = courses,
                        weekNumber = weekNumber,
                        periodTimes = periodTimes,
                        weekDaysCount = weekDays.size,
                        periodsCount = periods,
                        onCourseClick = { courseId -> onEditCourse(courseId, weekNumber) },
                        modifier = Modifier.weight(1f)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "第 $weekNumber 周 | 左右滑动切换周次",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            
            if (showWeekPicker) {
                WeekPickerDialog(
                    currentWeek = currentWeek,
                    onWeekSelected = { week ->
                        viewModel.setCurrentWeek(week)
                        scope.launch {
                            pagerState.scrollToPage(week - 1)
                        }
                        showWeekPicker = false
                    },
                    onDismiss = { showWeekPicker = false }
                )
            }
            }
        }
    }
}

@Composable
fun HyperOSTopAppBar(
    title: String,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (navigationIcon != null) {
                navigationIcon()
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            
            Row {
                actions()
            }
        }
    }
}

@Composable
fun WeekHeaderRow(
    currentWeek: Int,
    weekDays: List<String>,
    weekDates: List<String>,
    onWeekClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clickable { onWeekClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$currentWeek",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            weekDays.forEachIndexed { index, day ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = day,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp
                    )
                    Text(
                        text = weekDates[index],
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CourseGrid(
    courses: List<Course>,
    weekNumber: Int,
    periodTimes: List<com.example.a8319schedule.data.PeriodTime>,
    weekDaysCount: Int,
    periodsCount: Int,
    onCourseClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxSize()) {
        TimeColumn(periodTimes = periodTimes, periodsCount = periodsCount)
        
        repeat(weekDaysCount) { dayIndex ->
            DayColumn(
                dayIndex = dayIndex,
                courses = courses,
                weekNumber = weekNumber,
                onCourseClick = onCourseClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TimeColumn(
    periodTimes: List<PeriodTime>,
    periodsCount: Int
) {
    Column(
        modifier = Modifier
            .width(42.dp)
            .fillMaxHeight()
    ) {
        for (bigPeriod in 0 until periodsCount) {
            val periodTime = periodTimes.getOrNull(bigPeriod)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${bigPeriod + 1}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 15.sp,
                        maxLines = 1
                    )
                    if (periodTime != null) {
                        Text(
                            text = periodTime.startTime,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 11.sp,
                            maxLines = 1
                        )
                        Text(
                            text = periodTime.endTime,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DayColumn(
    dayIndex: Int,
    courses: List<Course>,
    weekNumber: Int,
    onCourseClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayCourses = remember(dayIndex, weekNumber, courses) {
        val filtered = courses.filter { course ->
            course.dayOfWeek == dayIndex + 1 && weekNumber == course.weekNumber
        }.sortedBy { it.startPeriod }
        
        filtered
    }
    
    Column(modifier = modifier.fillMaxHeight()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            BackgroundGrid(periodsCount = 5)
            
            dayCourses.forEach { course ->
                CourseLayoutItem(
                    course = course,
                    periodsCount = 5,
                    onClick = { onCourseClick(course.id) }
                )
            }
        }
    }
}

@Composable
fun BackgroundGrid(periodsCount: Int) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (bigPeriod in 1..periodsCount) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
            )
        }
    }
}

@Composable
fun CourseLayoutItem(
    course: Course,
    periodsCount: Int,
    onClick: () -> Unit
) {
    val startBigPeriod = ((course.startPeriod - 1) / 2)
    val endBigPeriod = ((course.endPeriod - 1) / 2)
    
    val startOffset = if (course.startPeriod % 2 == 1) 0f else 0.5f
    val endOffset = if (course.endPeriod % 2 == 0) 0f else 0.5f
    
    Column(modifier = Modifier.fillMaxSize()) {
        if (startBigPeriod > 0) {
            Box(modifier = Modifier.weight(startBigPeriod.toFloat()))
        }
        
        if (startOffset > 0) {
            Box(modifier = Modifier.weight(startOffset))
        }
        
        Box(modifier = Modifier.weight(endBigPeriod - startBigPeriod + 1 - startOffset - endOffset)) {
            CourseCard(
                course = course,
                onClick = { onClick() }
            )
        }
        
        if (endOffset > 0) {
            Box(modifier = Modifier.weight(endOffset))
        }
        
        if (endBigPeriod < periodsCount - 1) {
            Box(modifier = Modifier.weight((periodsCount - 1 - endBigPeriod).toFloat()))
        }
    }
}

@Composable
fun WeekPickerDialog(
    currentWeek: Int,
    onWeekSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedWeek by remember { mutableStateOf(currentWeek) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "选择周次",
                fontWeight = FontWeight.SemiBold
            ) 
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "当前: 第 $selectedWeek 周",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 周次选择网格
                val rows = 4
                val cols = 5
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (row in 0 until rows) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (col in 0 until cols) {
                                val week = row * cols + col + 1
                                if (week <= 20) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clickable { selectedWeek = week },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (week == selectedWeek) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$week",
                                                fontWeight = FontWeight.Medium,
                                                color = if (week == selectedWeek) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onWeekSelected(selectedWeek) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
