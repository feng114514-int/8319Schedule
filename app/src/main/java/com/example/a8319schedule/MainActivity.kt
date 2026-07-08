package com.example.a8319schedule

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.data.ScheduleSettingsManager
import com.example.a8319schedule.ui.screens.AddEditCourseScreen
import com.example.a8319schedule.ui.screens.EditThisWeekCourseScreen
import com.example.a8319schedule.ui.screens.ImportScreen
import com.example.a8319schedule.ui.screens.ScheduleScreen
import com.example.a8319schedule.ui.screens.ScheduleSettingsScreen
import com.example.a8319schedule.ui.screens.ScheduleListScreen
import com.example.a8319schedule.ui.screens.MyScreen
import com.example.a8319schedule.ui.screens.WidgetSettingsScreen
import com.example.a8319schedule.ui.screens.NotificationSettingsScreen
import com.example.a8319schedule.ui.screens.ExportScreen
import com.example.a8319schedule.ui.screens.AISettingsScreen
import com.example.a8319schedule.ui.screens.AIChatScreen
import com.example.a8319schedule.ui.screens.AIVisionImportScreen
import com.example.a8319schedule.ui.screens.ExamListScreen
import com.example.a8319schedule.ui.screens.TrainingPlanScreen
import com.example.a8319schedule.ui.screens.ScoreListScreen
import com.example.a8319schedule.ui.screens.ShareCodeScreen
import com.example.a8319schedule.ui.screens.UpdateOtherInfoScreen
import com.example.a8319schedule.ui.components.CourseDetailDialog
import com.example.a8319schedule.ui.theme._8319ScheduleTheme
import com.example.a8319schedule.viewmodel.CourseViewModel
import com.example.a8319schedule.viewmodel.ScheduleViewModel
import com.example.a8319schedule.viewmodel.ExamViewModel
import com.example.a8319schedule.viewmodel.TrainingPlanViewModel
import com.example.a8319schedule.viewmodel.AIChatViewModel
import com.example.a8319schedule.data.CourseDatabase
import com.example.a8319schedule.data.Course
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.utils.*

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        AppLifecycleListener.initialize(this)
        
        // 注册数据库变更观察者，课程数据变化时自动刷新小部件
        CourseDataObserver.getInstanceAndRegister(applicationContext)
        
        setContent {
            _8319ScheduleTheme {
                ScheduleApp()
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        // 应用恢复时刷新小部件，确保显示最新课程数据
        ScheduleWidgetProvider.forceUpdateAllWidgets(applicationContext)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
    }
    
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
fun ScheduleApp() {
    val viewModel: CourseViewModel = viewModel()
    val scheduleViewModel: ScheduleViewModel = viewModel()
    val aiChatViewModel: AIChatViewModel = viewModel()
    val examViewModel: ExamViewModel = viewModel()
    val trainingPlanViewModel: TrainingPlanViewModel = viewModel()
    val context = LocalContext.current
    val settingsManager = remember { ScheduleSettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    val currentWeek by viewModel.currentWeek.collectAsState()
    val activeScheduleId by scheduleViewModel.activeScheduleId.collectAsState()
    
    LaunchedEffect(activeScheduleId) {
        viewModel.setActiveScheduleId(activeScheduleId)
    }
    
    var showAddEditScreen by remember { mutableStateOf(false) }
    var editingCourseId by remember { mutableStateOf<Long?>(null) }
    var showImportScreen by remember { mutableStateOf(false) }
    var showSettingsScreen by remember { mutableStateOf(false) }
    var showWidgetSettings by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showExportScreen by remember { mutableStateOf(false) }
    var showScheduleListScreen by remember { mutableStateOf(false) }
    var showAISettingsScreen by remember { mutableStateOf(false) }
    var showAIChatScreen by remember { mutableStateOf(false) }
    var showAIVisionImportScreen by remember { mutableStateOf(false) }
    var showShareCodeScreen by remember { mutableStateOf(false) }
    var showExamListScreen by remember { mutableStateOf(false) }
    var showTrainingPlanScreen by remember { mutableStateOf(false) }
    var showScoreListScreen by remember { mutableStateOf(false) }
    var showUpdateOtherInfoScreen by remember { mutableStateOf(false) }

    var isFromMyScreen by remember { mutableStateOf(false) }
    
    var currentTab by remember { mutableStateOf(0) }
    var showMyScreen by remember { mutableStateOf(false) }
    
    var isColdStart by remember { mutableStateOf(true) }
    
    var showCourseDetailDialog by remember { mutableStateOf(false) }
    var detailCourseId by remember { mutableStateOf<Long?>(null) }
    
    var showEditCourseScreen by remember { mutableStateOf(false) }
    var editCourseId by remember { mutableStateOf<Long?>(null) }
    var editCourseInstanceId by remember { mutableStateOf<String?>(null) }
    var editCourseWeekNumber by remember { mutableStateOf(1) }
    
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val activity = LocalContext.current as MainActivity
    
    // HyperOS 风格的动画规格
    val hyperOSEnterTransition = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    ) + fadeIn(
        animationSpec = tween(250, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    )
    
    val hyperOSExitTransition = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    ) + fadeOut(
        animationSpec = tween(250, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
    )
    
    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    showCourseDetailDialog -> {
                        showCourseDetailDialog = false
                        detailCourseId = null
                    }
                    showEditCourseScreen -> {
                        showEditCourseScreen = false
                        editCourseId = null
                        editCourseInstanceId = null
                    }
                    showAddEditScreen -> {
                        showAddEditScreen = false
                    }
                    showImportScreen -> {
                        showImportScreen = false
                    }
                    showWidgetSettings -> {
                        showWidgetSettings = false
                    }
                    showNotificationSettings -> {
                        showNotificationSettings = false
                    }
                    showExportScreen -> {
                        showExportScreen = false
                    }
                    showSettingsScreen -> {
                        showSettingsScreen = false
                    }
                    showScheduleListScreen -> {
                        showScheduleListScreen = false
                    }
                    showAISettingsScreen -> {
                        showAISettingsScreen = false
                    }
                    showAIChatScreen -> {
                        showAIChatScreen = false
                    }
                    showAIVisionImportScreen -> {
                        showAIVisionImportScreen = false
                    }
                    showShareCodeScreen -> {
                        showShareCodeScreen = false
                    }
                    showExamListScreen -> {
                        showExamListScreen = false
                    }
                    showTrainingPlanScreen -> {
                        showTrainingPlanScreen = false
                    }
                    showScoreListScreen -> {
                        showScoreListScreen = false
                    }
                    showUpdateOtherInfoScreen -> {
                        showUpdateOtherInfoScreen = false
                    }
                    showMyScreen -> {
                        showMyScreen = false
                        currentTab = 0
                    }
                    else -> {
                        activity.finish()
                    }
                }
            }
        }
    }
    
    DisposableEffect(backDispatcher) {
        backDispatcher?.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }
    
    LaunchedEffect(showAddEditScreen, showEditCourseScreen, showImportScreen, showSettingsScreen, showWidgetSettings, showNotificationSettings, showExportScreen, showScheduleListScreen, showCourseDetailDialog, showMyScreen, showAISettingsScreen, showAIChatScreen, showShareCodeScreen, showExamListScreen, showTrainingPlanScreen, showScoreListScreen, showUpdateOtherInfoScreen) {
        backCallback.isEnabled = true
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
        
        val animSpec = tween<Int>(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
        
        val scheduleOffsetPx by animateIntAsState(
            targetValue = if (showMyScreen) -screenWidthPx else 0,
            animationSpec = animSpec,
            label = "scheduleOffset"
        )
        
        val myScreenOffsetPx by animateIntAsState(
            targetValue = if (showMyScreen) 0 else screenWidthPx,
            animationSpec = animSpec,
            label = "myScreenOffset"
        )
        
        val scheduleAlpha by animateFloatAsState(
            targetValue = if (showMyScreen) 0.7f else 1f,
            animationSpec = tween(300, easing = FastOutSlowInEasing),
            label = "scheduleAlpha"
        )
        
        Column(modifier = Modifier.fillMaxSize()) {
            // 主内容区域
            Box(modifier = Modifier.weight(1f)) {
                // 课表界面
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(scheduleOffsetPx, 0) }
                        .graphicsLayer { alpha = scheduleAlpha }
                ) {
                    ScheduleScreen(
                        key = "schedule_screen",
                        viewModel = viewModel,
                        scheduleViewModel = scheduleViewModel,
                        isColdStart = isColdStart,
                        onColdStartHandled = { isColdStart = false },
                        onAddCourse = {
                            editingCourseId = null
                            showAddEditScreen = true
                        },
                        onEditCourse = { courseId, _ ->
                            detailCourseId = courseId
                            showCourseDetailDialog = true
                        },
                        onFullEditCourse = { courseId ->
                            editingCourseId = courseId
                            showAddEditScreen = true
                        }
                    )
                }
            }
            
            // 底部导航栏 - HyperOS 风格
            HyperOSNavigationBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                    showMyScreen = tab == 1
                }
            )
        }
        
        // "我的"界面
        if (showMyScreen || myScreenOffsetPx != screenWidthPx) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(myScreenOffsetPx, 0) }
                    .zIndex(4f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            showMyScreen = false
                            currentTab = 0
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { }
                    ) {
                        MyScreen(
                            scheduleViewModel = scheduleViewModel,
                            onNavigateToScheduleManagement = {
                                showScheduleListScreen = true
                            },
                            onNavigateToSettings = {
                                showSettingsScreen = true
                            },
                            onNavigateToImport = {
                                showImportScreen = true
                            },
                            onNavigateToUpdateOtherInfo = {
                                showUpdateOtherInfoScreen = true
                            },
                            onNavigateToExamList = {
                                showExamListScreen = true
                            },
                            onNavigateToScore = {
                                showScoreListScreen = true
                            },
                            onNavigateToTrainingPlan = {
                                showTrainingPlanScreen = true
                            },
                            onNavigateToExport = {
                                showExportScreen = true
                                isFromMyScreen = true
                            },
                            onNavigateToWidgetSettings = {
                                showWidgetSettings = true
                                isFromMyScreen = true
                            },
                            onNavigateToNotificationSettings = {
                                showNotificationSettings = true
                            },
                            onNavigateToAISettings = {
                                showAISettingsScreen = true
                            },
                            onNavigateToAIChat = {
                                showAIChatScreen = true
                            },
                            onNavigateToAIVisionImport = {
                                showAIVisionImportScreen = true
                            },
                            onNavigateToShareCode = {
                                showShareCodeScreen = true
                            },
                            onBack = {
                                showMyScreen = false
                                currentTab = 0
                            }
                        )
                    }
                }
            }
        }
        
        // 浮动导航栏 - 在"我的"界面上方显示
        if (showMyScreen || myScreenOffsetPx != screenWidthPx) {
            HyperOSNavigationBar(
                currentTab = currentTab,
                onTabSelected = { tab ->
                    currentTab = tab
                    showMyScreen = tab == 1
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(5f)
            )
        }
        
        // 添加/编辑课程页面
        AnimatedVisibility(
            visible = showAddEditScreen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(400, easing = CubicBezierEasing(0.15f, 0.1f, 0.25f, 1f))
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(350, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                AddEditCourseScreen(
                    viewModel = viewModel,
                    courseId = editingCourseId,
                    onNavigateBack = {
                        showAddEditScreen = false
                    },
                    showControls = true,
                    onEditThisWeek = { courseId, courseInstanceId, weekNumber ->
                        editCourseId = courseId
                        editCourseInstanceId = courseInstanceId
                        editCourseWeekNumber = weekNumber
                        showEditCourseScreen = true
                    },
                    settingsManager = settingsManager
                )
            }
        }
        
        // 编辑本周课程
        AnimatedVisibility(
            visible = showEditCourseScreen,
            enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(350, easing = CubicBezierEasing(0.15f, 0.1f, 0.25f, 1f))
            ),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(
                targetScale = 0.9f,
                animationSpec = tween(300, easing = CubicBezierEasing(0.4f, 0f, 1f, 1f))
            ),
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                EditThisWeekCourseScreen(
                    viewModel = viewModel,
                    courseId = editCourseId ?: 0L,
                    courseInstanceId = editCourseInstanceId ?: "",
                    weekNumber = editCourseWeekNumber,
                    onNavigateBack = {
                        showEditCourseScreen = false
                        editCourseId = null
                        editCourseInstanceId = null
                        showAddEditScreen = false
                        editingCourseId = null
                    }
                )
            }
        }
        
        // 导入课表页面
        AnimatedVisibility(
            visible = showImportScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                ImportScreen(
                    viewModel = viewModel,
                    settingsManager = settingsManager,
                    onNavigateBack = {
                        showImportScreen = false
                    },
                    onNavigateToSettings = {
                        showImportScreen = false
                        showSettingsScreen = true
                    }
                )
            }
        }
        
        // 课表设置页面
        AnimatedVisibility(
            visible = showSettingsScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                ScheduleSettingsScreen(
                    settingsManager = settingsManager,
                    scheduleViewModel = scheduleViewModel,
                    onNavigateBack = {
                        showSettingsScreen = false
                    },
                    onSaveComplete = {
                        showSettingsScreen = false
                    }
                )
            }
        }
        
        // 小部件设置页面
        AnimatedVisibility(
            visible = showWidgetSettings,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                WidgetSettingsScreen(
                    onNavigateBack = {
                        showWidgetSettings = false
                    }
                )
            }
        }
        
        // 通知设置屏幕
        AnimatedVisibility(
            visible = showNotificationSettings,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                NotificationSettingsScreen(
                    viewModel = viewModel,
                    scheduleSettingsManager = settingsManager,
                    onNavigateBack = {
                        showNotificationSettings = false
                    }
                )
            }
        }
        
        // 课表列表屏幕
        AnimatedVisibility(
            visible = showScheduleListScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                ScheduleListScreen(
                    scheduleViewModel = scheduleViewModel,
                    onNavigateBack = {
                        showScheduleListScreen = false
                    },
                    onNavigateToSettings = { scheduleId ->
                        showScheduleListScreen = false
                        showSettingsScreen = true
                    }
                )
            }
        }
        
        // 导出屏幕
        AnimatedVisibility(
            visible = showExportScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                ExportScreen(
                    viewModel = viewModel,
                    settingsManager = settingsManager,
                    onNavigateBack = {
                        showExportScreen = false
                    }
                )
            }
        }
        
        // AI设置屏幕
        AnimatedVisibility(
            visible = showAISettingsScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                AISettingsScreen(
                    viewModel = aiChatViewModel,
                    onNavigateBack = {
                        showAISettingsScreen = false
                    },
                    onNavigateToChat = {
                        showAISettingsScreen = false
                        showAIChatScreen = true
                    }
                )
            }
        }
        
        // AI对话屏幕
        AnimatedVisibility(
            visible = showAIChatScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                AIChatScreen(
                    viewModel = aiChatViewModel,
                    onNavigateBack = {
                        showAIChatScreen = false
                    },
                    onNavigateToSettings = {
                        showAIChatScreen = false
                        showAISettingsScreen = true
                    }
                )
            }
        }

        // AI图片识别导入屏幕
        AnimatedVisibility(
            visible = showAIVisionImportScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                AIVisionImportScreen(
                    onNavigateBack = {
                        showAIVisionImportScreen = false
                    }
                )
            }
        }
        
        // 分享课表屏幕
        AnimatedVisibility(
            visible = showShareCodeScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                ShareCodeScreen(
                    onNavigateBack = {
                        showShareCodeScreen = false
                    }
                )
            }
        }
        
        // 考试安排屏幕
        AnimatedVisibility(
            visible = showExamListScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                ExamListScreen(
                    onNavigateBack = {
                        showExamListScreen = false
                    }
                )
            }
        }
        
        // 培养方案屏幕
        AnimatedVisibility(
            visible = showTrainingPlanScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                TrainingPlanScreen(
                    onNavigateBack = {
                        showTrainingPlanScreen = false
                    }
                )
            }
        }

        // 成绩查询屏幕
        AnimatedVisibility(
            visible = showScoreListScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                ScoreListScreen(
                    onNavigateBack = {
                        showScoreListScreen = false
                    }
                )
            }
        }

        // 更新其它信息屏幕
        AnimatedVisibility(
            visible = showUpdateOtherInfoScreen,
            enter = hyperOSEnterTransition,
            exit = hyperOSExitTransition,
            modifier = Modifier.zIndex(5f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
            ) {
                UpdateOtherInfoScreen(
                    scheduleViewModel = scheduleViewModel,
                    onNavigateBack = {
                        showUpdateOtherInfoScreen = false
                    }
                )
            }
        }

        // 课程详情弹窗
        val editScope = rememberCoroutineScope()
        var detailCourseState by remember { mutableStateOf<Course?>(null) }
        
        LaunchedEffect(detailCourseId) {
            detailCourseState = if (detailCourseId != null) {
                viewModel.getCourseById(detailCourseId!!)
            } else {
                null
            }
        }
        
        CourseDetailDialog(
            course = detailCourseState,
            weekNumber = currentWeek,
            isVisible = showCourseDetailDialog,
            onDismiss = { 
                showCourseDetailDialog = false
                detailCourseId = null
                detailCourseState = null
            },
            onEdit = { 
                showCourseDetailDialog = false
                detailCourseId = null
                detailCourseState?.let { course ->
                    editCourseId = course.id
                    editCourseInstanceId = course.courseInstanceId
                    editCourseWeekNumber = course.weekNumber
                    showEditCourseScreen = true
                }
                detailCourseState = null
            },
            onDelete = {
                detailCourseState?.let { course ->
                    editScope.launch {
                        viewModel.deleteCourse(course)
                    }
                }
                showCourseDetailDialog = false
                detailCourseId = null
                detailCourseState = null
            }
        )
    }
}

@Composable
fun HyperOSNavigationBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(surfaceColor)
            .navigationBarsPadding()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTabItem(
                selected = currentTab == 0,
                onClick = { onTabSelected(0) },
                icon = Icons.Default.CalendarMonth,
                label = "课表",
                selectedColor = primaryColor
            )
            
            NavigationTabItem(
                selected = currentTab == 1,
                onClick = { onTabSelected(1) },
                icon = Icons.Default.Person,
                label = "我的",
                selectedColor = primaryColor
            )
        }
    }
}

@Composable
private fun NavigationTabItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selectedColor: Color
) {
    val contentColor = if (selected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 12.sp
        )
    }
}
