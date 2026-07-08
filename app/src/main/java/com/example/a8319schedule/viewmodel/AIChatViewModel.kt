package com.example.a8319schedule.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.a8319schedule.data.*
import com.example.a8319schedule.data.CourseConflictDetector
import com.example.a8319schedule.ScheduleWidgetProvider
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.json.JSONObject
import java.util.UUID

class AIChatViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "AIChatViewModel"
    }
    
    private val settingsManager = AISettingsManager(application)
    private val memoryManager = AIChatMemoryManager(application)
    private lateinit var courseRepository: CourseRepository
    private lateinit var scheduleRepository: ScheduleRepository
    private lateinit var examDao: ExamDao
    private var aiService: AICourseService? = null
    
    // AI设置状态
    val aiSettings: StateFlow<AISettings> = settingsManager.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AISettings()
        )
    
    // 对话历史
    private val _conversationHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    val conversationHistory: StateFlow<List<ChatMessage>> = _conversationHistory.asStateFlow()
    
    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 错误信息
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // 是否有保存的上下文
    val hasContext: StateFlow<Boolean> = conversationHistory.map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    
    // 函数调用结果（用于多轮对话）
    private val functionResults = mutableListOf<FunctionCallResult>()
    
    // 流式输出任务
    private var streamJob: Job? = null
    
    // 是否已配置
    val isConfigured: StateFlow<Boolean> = aiSettings.map { 
        it.apiKey.isNotBlank() && it.enabled
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )
    
    init {
        val db = CourseDatabase.getDatabase(application)
        courseRepository = CourseRepository(db.courseDao())
        scheduleRepository = ScheduleRepository(db.scheduleInfoDao(), db.courseDao())
        examDao = db.examDao()

        // 加载保存的对话上下文
        viewModelScope.launch {
            val savedContext = memoryManager.loadContext()
            if (savedContext.isNotEmpty()) {
                _conversationHistory.value = savedContext
                Log.d(TAG, "已恢复 ${savedContext.size} 条对话上下文")
            }
        }

        // 监听设置变化，初始化AI服务
        viewModelScope.launch {
            aiSettings.collect { settings ->
                if (settings.apiKey.isNotBlank() && settings.enabled) {
                    // 确保 baseUrl 不为空，使用默认值
                    val effectiveBaseUrl = settings.baseUrl.ifBlank {
                        "https://api.openai.com/v1"
                    }
                    aiService = AICourseService(
                        apiKey = settings.apiKey,
                        model = settings.model,
                        baseUrl = effectiveBaseUrl
                    )
                }
            }
        }
    }
    
    /**
     * 发送消息（流式输出）
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val service = aiService
        if (service == null) {
            _error.value = "请先配置API密钥"
            return
        }
        
        // 取消之前的流式任务
        streamJob?.cancel()
        
        streamJob = viewModelScope.launch {
            try {
                _isLoading.value = true
                _error.value = null
                
                // 添加用户消息
                val userMessage = ChatMessage(role = "user", content = text)
                _conversationHistory.value = _conversationHistory.value + userMessage
                
                // 构建课程上下文
                val activeSchedule = scheduleRepository.getActiveSchedule()
                val activeScheduleId = activeSchedule?.id ?: 1L
                
                val currentTime = java.text.SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss EEEE", java.util.Locale.CHINA).format(java.util.Date())
                
                // 使用 WeekCalculator 计算当前是第几周
                val currentWeekNum = com.example.a8319schedule.data.WeekCalculator.calculateCurrentWeekForSchedule(activeSchedule)
                val cal = java.util.Calendar.getInstance()
                val dayOfWeekInt = cal.get(java.util.Calendar.DAY_OF_WEEK).let { if (it == 1) 7 else it - 1 }
                val dayOfWeekStr = when (dayOfWeekInt) {
                    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
                    5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "未知"
                }
                val tomorrowDow = if (dayOfWeekInt == 7) 1 else dayOfWeekInt + 1
                val tomorrowStr = when (tomorrowDow) {
                    1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
                    5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "未知"
                }
                
                val systemPrompt = buildString {
                    append(aiSettings.value.systemPrompt)
                    append("\n\n当前时间：")
                    append(currentTime)
                    append("\n今天是：")
                    append(dayOfWeekStr)
                    append("（对应dayOfWeek=")
                    append(dayOfWeekInt)
                    append("）")
                    append("\n本学期当前是第")
                    append(currentWeekNum)
                    append("周")
                    append("\n\n【重要日期说明】")
                    append("\n- 课表中dayOfWeek字段：1=周一, 2=周二, 3=周三, 4=周四, 5=周五, 6=周六, 7=周日")
                    append("\n- 用户说\"这周\"时，指的是第")
                    append(currentWeekNum)
                    append("周")
                    append("\n- 用户说\"今天\"时，指的是")
                    append(dayOfWeekStr)
                    append("(dayOfWeek=")
                    append(dayOfWeekInt)
                    append(")")
                    append("\n- 用户说\"明天\"时，指的是")
                    append(tomorrowStr)
                    append("(dayOfWeek=")
                    append(tomorrowDow)
                    append(")")
                    append("\n\n【课程时间表 —— 用于理解用户的时间表达】")
                    append("\n第1大节(上午第1节) = 第1-2节 (08:30-10:05)")
                    append("\n第2大节(上午第2节) = 第3-4节 (10:25-12:00)")
                    append("\n第3大节(下午第1节) = 第5-6节 (14:00-15:35)")
                    append("\n第4大节(下午第2节) = 第7-8节 (15:55-17:30)")
                    append("\n第5大节(晚上)      = 第9-10节 (19:00-20:35)")
                    append("\n⚠️ 用户说'下午第二大节'指第4大节=第7-8节(startPeriod=7,endPeriod=8)")
                    append("\n⚠️ 用户说'第五大节'指第5大节=第9-10节(startPeriod=9,endPeriod=10)")
                    append("\n⚠️ 大节编号绝不能直接当startPeriod用！")
                    append("\n\n【省略信息推断规则】")
                    append("\n- 用户未指定星期几时，默认为今天(dayOfWeek=")
                    append(dayOfWeekInt)
                    append(")")
                    append("\n- 用户未指定教师或教室时，先查课程信息中是否已有同名课程，有则复用，无则询问用户")
                    append("\n- 用户未指定周次时，默认为当前第")
                    append(currentWeekNum)
                    append("周")
                    append("\n\n【重要】你有以下工具可以使用：")
                    append("\n- query_schedule: 查询课表！当用户问课程相关问题时，必须调用此工具获取数据，不要凭记忆回答")
                    append("\n- add_course: 添加单门课程（仅添加一个周次）")
                    append("\n- batch_add_course: 批量添加多门课程")
                    append("\n- update_course: 修改单个课程的属性")
                    append("\n- update_course_weeks: 批量修改某个课程在特定周次的信息（推荐用于修改多周课程，一次调用即可）")
                    append("\n- delete_course: 删除整个课程（所有周次）")
                    append("\n- delete_course_weeks: 批量删除某个课程在特定周次的记录")
                    append("\n- set_notification_settings: 修改通知设置（上课提醒开关、提前提醒分钟数、每日课表摘要开关及推送时间），未提及的设置项保持不变")
                    append("\n- query_exams: 查询考试安排！当用户问考试相关问题时，必须调用此工具获取数据，不要凭记忆回答。返回结果含 examId，仅供后续修改/删除定位用，不要展示给用户")
                    append("\n- add_exam: 添加一条考试记录，必填课程名和考试时间（格式 \"yyyy-MM-dd HH:mm~HH:mm\"）")
                    append("\n- update_exam: 修改已有考试记录，必须先 query_exams 拿到 examId 再定位修改，未传字段保留原值")
                    append("\n- delete_exam: 删除考试记录，优先用 examId 精确删单条，无 examId 时用 courseName 删所有同名考试")
                    append("\n⚠️ 考试时间格式统一为 \"yyyy-MM-dd HH:mm~HH:mm\"，如 \"2026-06-29 10:25~12:05\"，不要用其它格式")
                    append("\n\n【重要】每次调用工具后，必须在 message.content 中包含对用户的说明文字，解释你做了什么！例如：")
                    append("\n- \"好的，已帮你把高数从周一改到周二。\"")
                    append("\n- \"已删除体育课的所有课程记录。\"")
                    append("\n- \"我已把第3、5、7周的高数教室改为301。\"")
                    append("\n\n【提示】当用户要添加跨多周的课程时（如\"添加高数，第1-10周周一1-2节\"），优先使用 batch_add_course 一次调用！")
                    append("\n        当用户要修改多周的同一课程时（如\"把第3、5、7周的高数改到下午\"），优先使用 update_course_weeks！")
                    append("\n        当用户要求修改多节课程时，允许调用多次工具，最终目的是实现用户需求")
                }
                
                // 添加一个空的assistant消息占位（流式填充）
                val assistantPlaceholder = ChatMessage(role = "assistant", content = "")
                _conversationHistory.value = _conversationHistory.value + assistantPlaceholder
                
                val fullText = StringBuilder()
                var toolCallsFromStream = listOf<FunctionCallResult>()
                
                // 收集流式输出
                service.chatStream(
                    messages = _conversationHistory.value.dropLast(1), // 去掉占位消息
                    systemPrompt = systemPrompt,
                    functionResults = emptyList() // 工具调用结果已包含在对话历史的tool消息中
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Text -> {
                            fullText.append(chunk.content)
                            // 实时更新最后一条消息
                            val current = _conversationHistory.value.toMutableList()
                            if (current.isNotEmpty() && current.last().role == "assistant") {
                                current[current.lastIndex] = current.last().copy(content = fullText.toString())
                                _conversationHistory.value = current
                            }
                        }
                        is StreamChunk.Done -> {
                            toolCallsFromStream = chunk.toolCalls
                        }
                        is StreamChunk.Error -> {
                            _error.value = chunk.message
                        }
                    }
                }
                
                // 确保最终文本写入，如果有工具调用则同时保存toolCalls信息
                val current = _conversationHistory.value.toMutableList()
                if (current.isNotEmpty() && current.last().role == "assistant") {
                    if (toolCallsFromStream.isNotEmpty()) {
                        // 工具调用时：把toolCalls写入assistant消息，确保发给API时格式正确
                        current[current.lastIndex] = current.last().copy(
                            content = fullText.toString(),
                            toolCalls = toolCallsFromStream
                        )
                    } else {
                        current[current.lastIndex] = current.last().copy(content = fullText.toString())
                    }
                    _conversationHistory.value = current
                }
                
                // 处理函数调用
                if (toolCallsFromStream.isNotEmpty()) {
                    toolCallsFromStream.forEach { call ->
                        val resultContent = executeFunction(call, activeScheduleId)
                        functionResults.add(FunctionCallResult(
                            toolCallId = call.toolCallId,
                            functionName = call.functionName,
                            arguments = call.arguments,
                            resultContent = resultContent
                        ))
                        // 将tool结果消息写入对话历史（role="tool"）
                        val toolMessage = ChatMessage(
                            role = "tool",
                            content = resultContent,
                            toolCallId = call.toolCallId
                        )
                        _conversationHistory.value = _conversationHistory.value + toolMessage
                    }
                    
                    // 函数调用完成后，再次流式调用AI获取最终回复
                    val secondPlaceholder = ChatMessage(role = "assistant", content = "")
                    _conversationHistory.value = _conversationHistory.value + secondPlaceholder
                    
                    val secondText = StringBuilder()
                    
                    service.chatStream(
                        messages = _conversationHistory.value.dropLast(1), // 去掉占位
                        systemPrompt = systemPrompt,
                        functionResults = emptyList() // 已包含在对话历史中
                    ).collect { chunk ->
                        when (chunk) {
                            is StreamChunk.Text -> {
                                secondText.append(chunk.content)
                                val current = _conversationHistory.value.toMutableList()
                                if (current.isNotEmpty() && current.last().role == "assistant") {
                                    current[current.lastIndex] = current.last().copy(content = secondText.toString())
                                    _conversationHistory.value = current
                                }
                            }
                            is StreamChunk.Done -> { }
                            is StreamChunk.Error -> {
                                _error.value = chunk.message
                            }
                        }
                    }
                    
                    // 确保最终文本写入，空消息则移除占位
                    val finalList = _conversationHistory.value.toMutableList()
                    if (finalList.isNotEmpty() && finalList.last().role == "assistant") {
                        if (secondText.isNotEmpty()) {
                            finalList[finalList.lastIndex] = finalList.last().copy(content = secondText.toString())
                        } else {
                            // 第二次AI也没返回文本，移除空的assistant占位
                            finalList.removeAt(finalList.lastIndex)
                        }
                        _conversationHistory.value = finalList
                    }
                    
                    ScheduleWidgetProvider.updateAllWidgets(getApplication())
                } else {
                    // 没有函数调用，如果文本为空则移除占位消息
                    if (fullText.isEmpty()) {
                        _conversationHistory.value = _conversationHistory.value.dropLast(1)
                    }
                }

                // 对话完成后，保存上下文到文件（异步，不阻塞用户）
                saveConversationContext()

            } catch (e: Exception) {
                Log.e(TAG, "发送消息失败: ${e.message}", e)
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * 执行函数调用
     */
    private suspend fun executeFunction(call: FunctionCallResult, scheduleId: Long): String {
        return try {
            val args = JSONObject(call.arguments)
            
            when (call.functionName) {
                "query_schedule" -> {
                    val allCourses = courseRepository.getCoursesByScheduleId(scheduleId).first()
                    if (allCourses.isEmpty()) {
                        return "当前课表为空，没有任何课程。"
                    }

                    val dayOfWeek = if (args.has("dayOfWeek")) args.optInt("dayOfWeek") else null
                    val weekNumber = if (args.has("weekNumber")) args.optInt("weekNumber") else null
                    val courseName = args.optString("courseName", "").trim()

                    var filtered = allCourses
                    if (dayOfWeek != null) filtered = filtered.filter { it.dayOfWeek == dayOfWeek }
                    if (weekNumber != null) filtered = filtered.filter { it.weekNumber == weekNumber }
                    if (courseName.isNotEmpty()) filtered = filtered.filter { it.name.contains(courseName, ignoreCase = true) }

                    if (filtered.isEmpty()) {
                        val hints = mutableListOf<String>()
                        if (dayOfWeek != null) hints.add("周${dayOfWeek}")
                        if (weekNumber != null) hints.add("第${weekNumber}周")
                        if (courseName.isNotEmpty()) hints.add("名称含「${courseName}」")
                        return "未找到${hints.joinToString("、")}的课程。当前课表共有${allCourses.size}条课程记录。"
                    }

                    // 按课程组汇总输出
                    val groups = filtered.groupBy { it.courseGroupId }
                    val sb = StringBuilder()
                    sb.append("找到${filtered.size}条课程记录：\n")
                    groups.forEach { (_, groupCourses) ->
                        val first = groupCourses.first()
                        val weeks = groupCourses.map { it.weekNumber }.sorted()
                        val weeksStr = formatWeeksCompact(weeks)
                        val dayStr = when (first.dayOfWeek) {
                            1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
                            5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "周${first.dayOfWeek}"
                        }
                        sb.append("• ${first.name} | ${first.teacher.ifEmpty { "无教师" }} | ${first.classroom.ifEmpty { "无教室" }} | $dayStr 第${first.startPeriod}-${first.endPeriod}节 | $weeksStr\n")
                    }
                    sb.toString()
                }
                
                "batch_add_course" -> {
                    val coursesArray = args.optJSONArray("courses")
                    if (coursesArray == null || coursesArray.length() == 0) {
                        return "错误：未提供课程列表"
                    }
                    
                    val allCourses = courseRepository.getCoursesByScheduleId(scheduleId).first()
                    val courseColors = com.example.a8319schedule.data.CourseColors.PALETTE
                    val usedColors = mutableSetOf<Long>().apply { addAll(allCourses.map { it.color }) }
                    
                    val results = mutableListOf<String>()
                    val nameColorMap = mutableMapOf<String, Long>()
                    val allConflicts = mutableListOf<String>()
                    
                    for (i in 0 until coursesArray.length()) {
                        val courseObj = coursesArray.getJSONObject(i)
                        val name = courseObj.optString("name", "新课程")
                        val teacher = courseObj.optString("teacher", "")
                        val classroom = courseObj.optString("classroom", "")
                        val dayOfWeek = courseObj.optInt("dayOfWeek", 1)
                        val weekNumber = courseObj.optInt("weekNumber", 1)
                        val startPeriod = courseObj.optInt("startPeriod", 1)
                        val endPeriod = courseObj.optInt("endPeriod", 5)
                        
                        // 冲突检测
                        val tempCourse = Course(
                            id = 0, name = name, dayOfWeek = dayOfWeek,
                            weekNumber = weekNumber, startPeriod = startPeriod,
                            endPeriod = endPeriod, scheduleId = scheduleId
                        )
                        val conflicts = CourseConflictDetector.detectConflicts(tempCourse, allCourses)
                        if (conflicts.isNotEmpty()) {
                            val conflictNames = CourseConflictDetector.formatConflictsBrief(conflicts)
                            allConflicts.add("「${name}」第${weekNumber}周周${dayOfWeek}第${startPeriod}-${endPeriod}节与${conflictNames}冲突")
                        }
                        
                        // 同名课程复用颜色
                        val color = nameColorMap.getOrPut(name) {
                            val sameNameCourse = allCourses.find { it.name == name }
                            val c = sameNameCourse?.color
                                ?: courseColors.firstOrNull { it !in usedColors }
                                ?: courseColors[allCourses.size % courseColors.size]
                            usedColors.add(c)
                            c
                        }
                        
                        val courseGroupId = "${name}_${teacher}_${classroom}_${startPeriod}_${System.currentTimeMillis()}"
                        
                        val newCourse = Course(
                            id = 0,
                            name = name,
                            teacher = teacher,
                            classroom = classroom,
                            dayOfWeek = dayOfWeek,
                            weekNumber = weekNumber,
                            startPeriod = startPeriod,
                            endPeriod = endPeriod,
                            color = color,
                            scheduleId = scheduleId,
                            courseInstanceId = UUID.randomUUID().toString(),
                            courseGroupId = courseGroupId
                        )
                        
                        courseRepository.insertCourse(newCourse)
                        results.add("「${name}」周${dayOfWeek}第${startPeriod}-${endPeriod}节(第${weekNumber}周)")
                    }
                    
                    Log.d(TAG, "批量添加课程成功: ${results.size}条")
                    val baseMsg = "已批量添加${results.size}条课程：${results.joinToString("、")}"
                    if (allConflicts.isNotEmpty()) {
                        "$baseMsg\n⚠️ 注意：${allConflicts.joinToString("；")}"
                    } else {
                        baseMsg
                    }
                }
                
                "add_course" -> {
                    val name = args.optString("name", "新课程")
                    val teacher = args.optString("teacher", "")
                    val classroom = args.optString("classroom", "")
                    val dayOfWeek = args.optInt("dayOfWeek", 1)
                    val weekNumber = args.optInt("weekNumber", 1)
                    val startPeriod = args.optInt("startPeriod", 1)
                    val endPeriod = args.optInt("endPeriod", 5)
                    
                    // 冲突检测
                    val allCourses = courseRepository.getCoursesByScheduleId(scheduleId).first()
                    val tempCourse = Course(
                        id = 0, name = name, dayOfWeek = dayOfWeek,
                        weekNumber = weekNumber, startPeriod = startPeriod,
                        endPeriod = endPeriod, scheduleId = scheduleId
                    )
                    val conflicts = CourseConflictDetector.detectConflicts(tempCourse, allCourses)
                    val conflictWarning = if (conflicts.isNotEmpty()) {
                        "\n⚠️ 注意：与${CourseConflictDetector.formatConflictsBrief(conflicts)}时间冲突，已添加但可能需要调整"
                    } else ""
                    
                    // 为新课程分配颜色：同名课程用已有颜色，否则选一个未使用的颜色
                    val courseColors = com.example.a8319schedule.data.CourseColors.PALETTE
                    val usedColors = allCourses.map { it.color }.toSet()
                    val sameNameCourse = allCourses.find { it.name == name }
                    val color = sameNameCourse?.color
                        ?: courseColors.firstOrNull { it !in usedColors }
                        ?: courseColors[allCourses.size % courseColors.size]
                    
                    val newCourse = Course(
                        id = 0,
                        name = name,
                        teacher = teacher,
                        classroom = classroom,
                        dayOfWeek = dayOfWeek,
                        weekNumber = weekNumber,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        color = color,
                        scheduleId = scheduleId,
                        courseInstanceId = UUID.randomUUID().toString(),
                        courseGroupId = "${name}_${teacher}_${classroom}_${startPeriod}_${System.currentTimeMillis()}"
                    )
                    
                    courseRepository.insertCourse(newCourse)
                    Log.d(TAG, "添加课程成功: $name")
                    "已添加「${name}」，时间为周${dayOfWeek}第${startPeriod}-${endPeriod}节$conflictWarning"
                }
                
                "update_course" -> {
                    val courseName = args.optString("course_name", "")
                    if (courseName.isBlank()) {
                        return "错误：未指定课程名称"
                    }
                    
                    val allCourses: List<Course> = courseRepository.getCoursesByScheduleId(scheduleId).first()
                    val courses = allCourses.filter { it.name == courseName }
                    
                    if (courses.isEmpty()) {
                        return "未找到名为「${courseName}」的课程"
                    }
                    
                    val conflictWarnings = mutableListOf<String>()
                    
                    courses.forEach { course ->
                        val newNameStr = args.optString("new_name", "")
                        val newTeacherStr = args.optString("new_teacher", "")
                        val newClassroomStr = args.optString("new_classroom", "")
                        val newName = if (newNameStr.isNotBlank()) newNameStr else course.name
                        val newTeacher = if (newTeacherStr.isNotBlank()) newTeacherStr else course.teacher
                        val newClassroom = if (newClassroomStr.isNotBlank()) newClassroomStr else course.classroom
                        val newDayOfWeek = if (args.has("new_dayOfWeek")) args.getInt("new_dayOfWeek") else course.dayOfWeek
                        val newStartPeriod = if (args.has("new_startPeriod")) args.getInt("new_startPeriod") else course.startPeriod
                        val newEndPeriod = if (args.has("new_endPeriod")) args.getInt("new_endPeriod") else course.endPeriod
                        
                        // 冲突检测（排除自身）
                        val tempCourse = Course(
                            id = 0, name = newName, dayOfWeek = newDayOfWeek,
                            weekNumber = course.weekNumber, startPeriod = newStartPeriod,
                            endPeriod = newEndPeriod, scheduleId = scheduleId
                        )
                        val conflicts = CourseConflictDetector.detectConflicts(tempCourse, allCourses, course.id)
                        if (conflicts.isNotEmpty()) {
                            val conflictNames = CourseConflictDetector.formatConflictsBrief(conflicts)
                            conflictWarnings.add("「${newName}」第${course.weekNumber}周与${conflictNames}冲突")
                        }
                        
                        val updatedCourse = Course(
                            id = course.id,
                            name = newName,
                            teacher = newTeacher,
                            classroom = newClassroom,
                            dayOfWeek = newDayOfWeek,
                            weekNumber = course.weekNumber,
                            startPeriod = newStartPeriod,
                            endPeriod = newEndPeriod,
                            color = course.color,
                            courseGroupId = course.courseGroupId,
                            courseInstanceId = course.courseInstanceId,
                            scheduleId = course.scheduleId
                        )
                        courseRepository.updateCourse(updatedCourse)
                    }
                    
                    Log.d(TAG, "更新课程成功: $courseName")
                    val baseMsg = "已将「${courseName}」修改完成"
                    if (conflictWarnings.isNotEmpty()) {
                        "$baseMsg\n⚠️ 注意：${conflictWarnings.joinToString("；")}"
                    } else {
                        baseMsg
                    }
                }
                
                "delete_course" -> {
                    val courseName = args.optString("course_name", "")
                    if (courseName.isBlank()) {
                        return "错误：未指定课程名称"
                    }
                    
                    val allCourses: List<Course> = courseRepository.getCoursesByScheduleId(scheduleId).first()
                    val courses = allCourses.filter { it.name == courseName }
                    
                    if (courses.isEmpty()) {
                        return "未找到名为「${courseName}」的课程"
                    }
                    
                    courses.forEach { course ->
                        courseRepository.deleteCourse(course)
                    }
                    
                    Log.d(TAG, "删除课程成功: $courseName, 共${courses.size}条")
                    "已删除「${courseName}」"
                }
                
                "update_course_weeks" -> {
                    val courseName = args.optString("course_name", "")
                    val weeksArray = args.optJSONArray("weeks")
                    if (courseName.isBlank()) {
                        return "错误：未指定课程名称"
                    }
                    if (weeksArray == null || weeksArray.length() == 0) {
                        return "错误：未指定要修改的周次"
                    }
                    
                    val weeks = mutableListOf<Int>()
                    for (i in 0 until weeksArray.length()) {
                        weeks.add(weeksArray.getInt(i))
                    }
                    
                    val allCourses: List<Course> = courseRepository.getCoursesByScheduleId(scheduleId).first()
                    val targetCourses = allCourses.filter { it.name == courseName && it.weekNumber in weeks }
                    
                    if (targetCourses.isEmpty()) {
                        return "未找到「${courseName}」在第${weeks.joinToString("、")}周的任何课程"
                    }
                    
                    val conflictWarnings = mutableListOf<String>()
                    var updatedCount = 0
                    
                    targetCourses.forEach { course ->
                        val newNameStr = args.optString("new_name", "")
                        val newTeacherStr = args.optString("new_teacher", "")
                        val newClassroomStr = args.optString("new_classroom", "")
                        val newName = if (newNameStr.isNotBlank()) newNameStr else course.name
                        val newTeacher = if (newTeacherStr.isNotBlank()) newTeacherStr else course.teacher
                        val newClassroom = if (newClassroomStr.isNotBlank()) newClassroomStr else course.classroom
                        val newDayOfWeek = if (args.has("new_dayOfWeek")) args.getInt("new_dayOfWeek") else course.dayOfWeek
                        val newStartPeriod = if (args.has("new_startPeriod")) args.getInt("new_startPeriod") else course.startPeriod
                        val newEndPeriod = if (args.has("new_endPeriod")) args.getInt("new_endPeriod") else course.endPeriod
                        
                        // 冲突检测（排除自身）
                        val tempCourse = Course(
                            id = 0, name = newName, dayOfWeek = newDayOfWeek,
                            weekNumber = course.weekNumber, startPeriod = newStartPeriod,
                            endPeriod = newEndPeriod, scheduleId = scheduleId
                        )
                        val conflicts = CourseConflictDetector.detectConflicts(tempCourse, allCourses, course.id)
                        if (conflicts.isNotEmpty()) {
                            val conflictNames = CourseConflictDetector.formatConflictsBrief(conflicts)
                            conflictWarnings.add("「${newName}」第${course.weekNumber}周与${conflictNames}冲突")
                        }
                        
                        val updatedCourse = Course(
                            id = course.id,
                            name = newName,
                            teacher = newTeacher,
                            classroom = newClassroom,
                            dayOfWeek = newDayOfWeek,
                            weekNumber = course.weekNumber,
                            startPeriod = newStartPeriod,
                            endPeriod = newEndPeriod,
                            color = course.color,
                            courseGroupId = course.courseGroupId,
                            courseInstanceId = course.courseInstanceId,
                            scheduleId = course.scheduleId
                        )
                        courseRepository.updateCourse(updatedCourse)
                        updatedCount++
                    }
                    
                    Log.d(TAG, "批量更新课程成功: $courseName 第${weeks.joinToString("、")}周, 共${updatedCount}条")
                    val baseMsg = "已将「${courseName}」第${weeks.joinToString("、")}周共${updatedCount}条记录修改完成"
                    if (conflictWarnings.isNotEmpty()) {
                        "$baseMsg\n⚠️ 注意：${conflictWarnings.joinToString("；")}"
                    } else {
                        baseMsg
                    }
                }
                
                "delete_course_weeks" -> {
                    val courseName = args.optString("course_name", "")
                    val weeksArray = args.optJSONArray("weeks")
                    if (courseName.isBlank()) {
                        return "错误：未指定课程名称"
                    }
                    if (weeksArray == null || weeksArray.length() == 0) {
                        return "错误：未指定要删除的周次"
                    }
                    
                    val weeks = mutableListOf<Int>()
                    for (i in 0 until weeksArray.length()) {
                        weeks.add(weeksArray.getInt(i))
                    }
                    
                    val allCourses: List<Course> = courseRepository.getCoursesByScheduleId(scheduleId).first()
                    val targetCourses = allCourses.filter { it.name == courseName && it.weekNumber in weeks }
                    
                    if (targetCourses.isEmpty()) {
                        return "未找到「${courseName}」在第${weeks.joinToString("、")}周的任何课程"
                    }
                    
                    targetCourses.forEach { course ->
                        courseRepository.deleteCourse(course)
                    }
                    
                    Log.d(TAG, "批量删除课程成功: $courseName 第${weeks.joinToString("、")}周, 共${targetCourses.size}条")
                    "已删除「${courseName}」第${weeks.joinToString("、")}周共${targetCourses.size}条记录"
                }
                
                "set_notification_settings" -> {
                    val notifManager = NotificationSettingsManager(getApplication<Application>())
                    val currentSettings = notifManager.settings.first()

                    val newCourseReminderEnabled = if (args.has("courseReminderEnabled")) args.getBoolean("courseReminderEnabled") else currentSettings.courseReminderEnabled
                    val newReminderMinutesBefore = if (args.has("reminderMinutesBefore")) args.getInt("reminderMinutesBefore") else currentSettings.reminderMinutesBefore
                    val newDailySummaryEnabled = if (args.has("dailySummaryEnabled")) args.getBoolean("dailySummaryEnabled") else currentSettings.dailySummaryEnabled
                    val newDailySummaryHour = if (args.has("dailySummaryHour")) args.getInt("dailySummaryHour") else currentSettings.dailySummaryHour
                    val newDailySummaryMinute = if (args.has("dailySummaryMinute")) args.getInt("dailySummaryMinute") else currentSettings.dailySummaryMinute

                    val validMinutes = listOf(5, 10, 15, 20, 30, 45, 60)
                    val finalMinutes = if (newReminderMinutesBefore in validMinutes) newReminderMinutesBefore else currentSettings.reminderMinutesBefore
                    val finalHour = newDailySummaryHour.coerceIn(0, 23)
                    val finalMinute = newDailySummaryMinute.coerceIn(0, 59)

                    val newSettings = NotificationSettings(
                        courseReminderEnabled = newCourseReminderEnabled,
                        reminderMinutesBefore = finalMinutes,
                        dailySummaryEnabled = newDailySummaryEnabled,
                        dailySummaryHour = finalHour,
                        dailySummaryMinute = finalMinute
                    )
                    notifManager.updateSettings(newSettings)

                    // 刷新闹钟调度
                    com.example.a8319schedule.CourseAlarmScheduler.rescheduleAll(getApplication<Application>())

                    val changes = mutableListOf<String>()
                    if (args.has("courseReminderEnabled")) {
                        changes.add(if (newCourseReminderEnabled) "上课提醒已开启" else "上课提醒已关闭")
                    }
                    if (args.has("reminderMinutesBefore")) {
                        changes.add("提前提醒时间设为${finalMinutes}分钟")
                    }
                    if (args.has("dailySummaryEnabled")) {
                        changes.add(if (newDailySummaryEnabled) "每日课表摘要已开启" else "每日课表摘要已关闭")
                    }
                    if (args.has("dailySummaryHour") || args.has("dailySummaryMinute")) {
                        changes.add("每日摘要推送时间设为${String.format("%02d:%02d", finalHour, finalMinute)}")
                    }

                    if (changes.isEmpty()) {
                        "当前通知设置：上课提醒${if (currentSettings.courseReminderEnabled) "已开启(提前${currentSettings.reminderMinutesBefore}分钟)" else "已关闭"}，每日摘要${if (currentSettings.dailySummaryEnabled) "已开启(${String.format("%02d:%02d", currentSettings.dailySummaryHour, currentSettings.dailySummaryMinute)})" else "已关闭"}"
                    } else {
                        "通知设置已更新：${changes.joinToString("，")}"
                    }
                }

                "query_exams" -> {
                    val allExams = examDao.getExamsListByScheduleId(scheduleId)
                    if (allExams.isEmpty()) {
                        return "当前课表暂无考试安排。"
                    }
                    val courseName = args.optString("courseName", "").trim()
                    val filtered = if (courseName.isNotEmpty())
                        allExams.filter { it.courseName.contains(courseName, ignoreCase = true) }
                    else allExams
                    if (filtered.isEmpty()) {
                        return "未找到名称含「${courseName}」的考试。当前课表共有${allExams.size}条考试记录。"
                    }
                    val sb = StringBuilder("找到${filtered.size}条考试记录（examId仅用于后续修改/删除定位，不要展示给用户）：\n")
                    filtered.forEach {
                        sb.append("• [examId=${it.id}] ${it.courseName} | ${it.examTimeRaw}")
                        if (it.examRoom.isNotBlank()) sb.append(" | 考场${it.examRoom}")
                        if (it.seatNumber.isNotBlank()) sb.append(" | 座位${it.seatNumber}")
                        if (it.teacher.isNotBlank()) sb.append(" | 教师${it.teacher}")
                        if (it.campus.isNotBlank()) sb.append(" | ${it.campus}")
                        sb.append("\n")
                    }
                    sb.toString().trimEnd()
                }

                "add_exam" -> {
                    val courseName = args.optString("courseName", "").trim()
                    val examTimeRaw = args.optString("examTimeRaw", "").trim()
                    if (courseName.isBlank()) {
                        return "错误：未指定课程名称"
                    }
                    if (examTimeRaw.isBlank()) {
                        return "错误：未指定考试时间，格式需为 \"yyyy-MM-dd HH:mm~HH:mm\""
                    }
                    val (startTs, endTs) = ExamParser.parseTime(examTimeRaw)
                    if (startTs <= 0) {
                        return "错误：考试时间格式不正确，需为 \"yyyy-MM-dd HH:mm~HH:mm\"（如 \"2026-06-29 10:25~12:05\"），请向用户确认时间"
                    }
                    val exam = Exam(
                        scheduleId = scheduleId,
                        courseName = courseName,
                        courseCode = args.optString("courseCode", "").trim(),
                        sessionName = args.optString("sessionName", "").trim(),
                        campus = args.optString("campus", "").trim(),
                        teacher = args.optString("teacher", "").trim(),
                        invigilator = "",
                        examTimeRaw = examTimeRaw,
                        examStartTimestamp = startTs,
                        examEndTimestamp = endTs,
                        examRoom = args.optString("examRoom", "").trim(),
                        seatNumber = args.optString("seatNumber", "").trim(),
                        admissionTicket = "",
                        remark = args.optString("remark", "").trim(),
                        xnxqid = ""
                    )
                    val newId = examDao.insertExam(exam)
                    Log.d(TAG, "添加考试成功: $courseName, id=$newId")
                    "已添加考试「${courseName}」，时间${examTimeRaw}"
                }

                "update_exam" -> {
                    val examId = args.optLong("examId", -1L)
                    if (examId <= 0) {
                        return "错误：未提供有效的 examId，请先调用 query_exams 获取"
                    }
                    val allExams = examDao.getExamsListByScheduleId(scheduleId)
                    val original = allExams.find { it.id == examId }
                        ?: return "错误：未找到 examId=${examId} 的考试记录"

                    val newCourseName = args.optString("new_courseName", "").trim()
                    val newExamTimeRaw = args.optString("new_examTimeRaw", "").trim()
                    val (newStartTs, newEndTs) = if (newExamTimeRaw.isNotEmpty()) {
                        val (s, e) = ExamParser.parseTime(newExamTimeRaw)
                        if (s <= 0) {
                            return "错误：新的考试时间格式不正确，需为 \"yyyy-MM-dd HH:mm~HH:mm\""
                        }
                        s to e
                    } else {
                        original.examStartTimestamp to original.examEndTimestamp
                    }
                    val finalExamTimeRaw = newExamTimeRaw.ifEmpty { original.examTimeRaw }

                    val updated = original.copy(
                        courseName = newCourseName.ifEmpty { original.courseName },
                        courseCode = args.optString("new_courseCode", "").trim().ifEmpty { original.courseCode },
                        sessionName = args.optString("new_sessionName", "").trim().ifEmpty { original.sessionName },
                        campus = args.optString("new_campus", "").trim().ifEmpty { original.campus },
                        teacher = args.optString("new_teacher", "").trim().ifEmpty { original.teacher },
                        examTimeRaw = finalExamTimeRaw,
                        examStartTimestamp = newStartTs,
                        examEndTimestamp = newEndTs,
                        examRoom = args.optString("new_examRoom", "").trim().ifEmpty { original.examRoom },
                        seatNumber = args.optString("new_seatNumber", "").trim().ifEmpty { original.seatNumber },
                        remark = args.optString("new_remark", "").trim().ifEmpty { original.remark }
                    )
                    examDao.insertExam(updated) // 利用 REPLACE 策略实现更新（同 id 覆盖）
                    Log.d(TAG, "更新考试成功: examId=$examId, 课程=${updated.courseName}")
                    "已修改考试「${updated.courseName}」"
                }

                "delete_exam" -> {
                    val examId = args.optLong("examId", -1L)
                    if (examId > 0) {
                        val allExams = examDao.getExamsListByScheduleId(scheduleId)
                        val target = allExams.find { it.id == examId }
                            ?: return "错误：未找到 examId=${examId} 的考试记录"
                        examDao.deleteExamById(examId)
                        Log.d(TAG, "删除考试成功: examId=$examId, 课程=${target.courseName}")
                        "已删除考试「${target.courseName}」"
                    } else {
                        val courseName = args.optString("courseName", "").trim()
                        if (courseName.isBlank()) {
                            return "错误：需提供 examId 或 courseName 之一"
                        }
                        val allExams = examDao.getExamsListByScheduleId(scheduleId)
                        val targets = allExams.filter { it.courseName == courseName }
                        if (targets.isEmpty()) {
                            return "未找到名为「${courseName}」的考试记录"
                        }
                        targets.forEach { examDao.deleteExamById(it.id) }
                        Log.d(TAG, "删除考试成功: $courseName, 共${targets.size}条")
                        "已删除「${courseName}」共${targets.size}条考试记录"
                    }
                }

                else -> {
                    Log.w(TAG, "未知函数: ${call.functionName}")
                    "未知函数: ${call.functionName}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行函数失败: ${e.message}", e)
            "执行失败: ${e.message}"
        }
    }
    
    /**
     * 清空对话历史（同时删除保存的上下文）
     */
    fun clearConversation() {
        _conversationHistory.value = emptyList()
        functionResults.clear()
        deleteContext()
    }

    /**
     * 保存当前对话上下文到文件
     */
    private fun saveConversationContext() {
        viewModelScope.launch {
            try {
                memoryManager.saveContext(_conversationHistory.value)
            } catch (e: Exception) {
                Log.e(TAG, "保存上下文失败: ${e.message}", e)
            }
        }
    }

    /**
     * 删除保存的对话上下文
     */
    fun deleteContext() {
        viewModelScope.launch {
            memoryManager.deleteContext()
        }
    }
    
    /**
     * 保存AI设置
     */
    fun saveSettings(settings: AISettings) {
        viewModelScope.launch {
            settingsManager.saveSettings(settings)
        }
    }
    
    /**
     * 更新API密钥
     */
    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            settingsManager.saveApiKey(apiKey)
        }
    }
    
    /**
     * 更新模型
     */
    fun updateModel(model: String) {
        viewModelScope.launch {
            settingsManager.saveModel(model)
        }
    }
    
    /**
     * 更新Base URL
     */
    fun updateBaseUrl(url: String) {
        viewModelScope.launch {
            settingsManager.saveBaseUrl(url)
        }
    }
    
    /**
     * 启用/禁用AI助手
     */
    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setEnabled(enabled)
        }
    }
    
    /**
     * 清除错误
     */
    fun clearError() {
        _error.value = null
    }
    
    // 使用统一的 WeekCalculator 计算当前周次
    private fun calculateCurrentWeek(schedule: com.example.a8319schedule.data.ScheduleInfo?): Int {
        return com.example.a8319schedule.data.WeekCalculator.calculateCurrentWeekForSchedule(schedule)
    }

    /**
     * 格式化周次列表为紧凑格式，如 1-5,7,9-12
     */
    private fun formatWeeksCompact(weeks: List<Int>): String {
        if (weeks.isEmpty()) return "无"
        val sorted = weeks.sorted()
        val ranges = mutableListOf<String>()
        var start = sorted[0]
        var end = sorted[0]
        for (i in 1 until sorted.size) {
            if (sorted[i] == end + 1) { end = sorted[i] }
            else { ranges.add(if (start == end) "$start" else "$start-$end"); start = sorted[i]; end = sorted[i] }
        }
        ranges.add(if (start == end) "$start" else "$start-$end")
        return ranges.joinToString(",")
    }
}
