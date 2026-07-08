package com.example.a8319schedule.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton as M3TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import com.example.a8319schedule.data.AICourseService
import com.example.a8319schedule.data.Course
import com.example.a8319schedule.viewmodel.AIChatViewModel
import com.example.a8319schedule.viewmodel.CourseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.*
import java.io.ByteArrayOutputStream

@Composable
fun AIVisionImportScreen(
    viewModel: AIChatViewModel = viewModel(),
    courseViewModel: CourseViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiSettings by viewModel.aiSettings.collectAsState()

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var parseResult by remember { mutableStateOf<ParseImageResult?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var importedCourses by remember { mutableStateOf<List<Course>>(emptyList()) }

    // 处理返回键
    BackHandler(enabled = true) {
        onNavigateBack()
    }

    // 相机拍照
    val cameraImageUri = remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && cameraImageUri.value != null) {
            loadImageFromUri(context, cameraImageUri.value!!) { bitmap, bytes ->
                selectedBitmap = bitmap
                selectedImageBytes = bytes
            }
        }
    }

    // 相册选择
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            loadImageFromUri(context, it) { bitmap, bytes ->
                selectedBitmap = bitmap
                selectedImageBytes = bytes
            }
        }
    }

    // 相机权限
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createImageUri(context)
            cameraImageUri.value = uri
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            HyperOSScreenTopBar(
                title = "AI识课表",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 说明卡片
            HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AI识别课表",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "拍摄或选择一张课表图片，AI将自动识别并导入课程。\n请确保图片清晰，课表完整可见。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // API配置检查
            if (!aiSettings.enabled || aiSettings.apiKey.isBlank()) {
                HyperOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors().copy(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "⚠️ 请先配置AI助手",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "需要在「我的」-「AI助手设置」中配置API密钥，且模型需支持图片识别（如GPT-4o、Claude 3等）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 图片预览区域
            if (selectedBitmap != null) {
                HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "已选择图片",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "课表图片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                text = "重新选择",
                                onClick = {
                                    selectedBitmap = null
                                    selectedImageBytes = null
                                }
                            )
                            Button(
                                onClick = { showPreviewDialog = true },
                                cornerRadius = 8.dp
                            ) {
                                Text("查看大图")
                            }
                        }
                    }
                }
            }

            // 操作按钮
            if (selectedBitmap == null) {
                Spacer(modifier = Modifier.height(32.dp))
                // 拍照按钮
                ImportMethodCard(
                    icon = Icons.Default.PhotoCamera,
                    title = "拍照识别",
                    description = "拍摄课表照片",
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            val uri = createImageUri(context)
                            cameraImageUri.value = uri
                            cameraLauncher.launch(uri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                // 相册按钮
                ImportMethodCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = "从相册选择",
                    description = "选择课表截图或照片",
                    onClick = {
                        galleryLauncher.launch("image/*")
                    }
                )
            } else {
                // 识别按钮
                Button(
                    onClick = {
                        if (selectedImageBytes == null) {
                            Toast.makeText(context, "图片数据为空", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        scope.launch {
                            try {
                                // 确保 baseUrl 不为空，使用默认值
                                val effectiveBaseUrl = aiSettings.baseUrl.ifBlank {
                                    "https://api.openai.com/v1"
                                }
                                val service = AICourseService(
                                    apiKey = aiSettings.apiKey,
                                    model = aiSettings.model,
                                    baseUrl = effectiveBaseUrl
                                )
                                val base64 = Base64.encodeToString(selectedImageBytes, Base64.NO_WRAP)
                                val result = service.parseScheduleImage(base64, "image/jpeg")
                                result.fold(
                                    onSuccess = { jsonStr ->
                                        parseResult = parseImageJson(jsonStr)
                                        showResultDialog = true
                                    },
                                    onFailure = { e ->
                                        Toast.makeText(context, "识别失败: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "识别异常: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    enabled = !isLoading && aiSettings.enabled && aiSettings.apiKey.isNotBlank()
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isLoading) "AI识别中..." else "开始识别")
                }
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // 识别结果弹窗
    if (showResultDialog && parseResult != null) {
        val result = parseResult!!
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            icon = {
                Icon(
                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(if (result.success) "识别成功" else "识别失败")
            },
            text = {
                Column {
                    if (result.success) {
                        Text("共识别到 ${result.courses.size} 门课程：")
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            items(result.courses) { course ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        "• ${course.name} ${course.teacher} ${course.classroom}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text(
                                        "  周${course.dayOfWeek} 第${course.startPeriod}-${course.endPeriod}节 (第${course.weekNumber}周)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Text(result.message)
                    }
                }
            },
            confirmButton = {
                if (result.success) {
                    Button(onClick = {
                        scope.launch {
                            result.courses.forEach { course ->
                                courseViewModel.addCourse(course)
                            }
                            importedCourses = result.courses
                            showResultDialog = false
                            Toast.makeText(context, "已导入 ${result.courses.size} 门课程", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        }
                    }) {
                        Text("确认导入")
                    }
                } else {
                    M3TextButton(onClick = { showResultDialog = false }) {
                        Text("关闭")
                    }
                }
            },
            dismissButton = {
                M3TextButton(onClick = { showResultDialog = false }) {
                    Text(if (result.success) "取消" else "关闭")
                }
            }
        )
    }

    // 图片预览大图
    if (showPreviewDialog && selectedBitmap != null) {
        Dialog(onDismissRequest = { showPreviewDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
                    .clickable { showPreviewDialog = false },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = selectedBitmap!!.asImageBitmap(),
                    contentDescription = "大图预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}

/** 导入方式卡片 */
@Composable
private fun ImportMethodCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    HyperOSCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/** 解析AI返回的JSON */
private fun parseImageJson(jsonStr: String): ParseImageResult {
    return try {
        // AI可能返回带```json```包裹的内容，先清理
        val cleaned = jsonStr
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonObject = JSONObject(cleaned)
        val success = jsonObject.optBoolean("success", true)
        if (!success) {
            return ParseImageResult(
                success = false,
                message = jsonObject.optString("message", "识别失败")
            )
        }

        val coursesArray = jsonObject.optJSONArray("courses") ?: JSONArray()
        val courses = mutableListOf<Course>()
        val courseColors = com.example.a8319schedule.data.CourseColors.PALETTE
        val colorMap = mutableMapOf<String, Long>()
        var colorIndex = 0
        val activeScheduleId = 1L // 默认课表

        for (i in 0 until coursesArray.length()) {
            val obj = coursesArray.getJSONObject(i)
            val name = obj.optString("name", "")
            if (name.isBlank()) continue

            val teacher = obj.optString("teacher", "")
            val classroom = obj.optString("classroom", "")
            val dayOfWeek = obj.optInt("dayOfWeek", 1).coerceIn(1, 7)
            val startPeriod = obj.optInt("startPeriod", 1).coerceIn(1, 10)
            val endPeriod = obj.optInt("endPeriod", startPeriod).coerceIn(1, 10)
            val weekRange = obj.optString("weekRange", "1-16")

            // 解析周次范围
            val weekNumbers = parseWeekRange(weekRange)

            // 分配颜色
            val color = colorMap.getOrPut(name) {
                val c = courseColors.getOrNull(colorIndex % courseColors.size) ?: courseColors[0]
                colorIndex++
                c
            }

            val courseGroupId = "${name}_${teacher}_${classroom}_${startPeriod}_${System.currentTimeMillis()}"

            weekNumbers.forEach { weekNum ->
                courses.add(
                    Course(
                        id = 0,
                        name = name,
                        teacher = teacher,
                        classroom = classroom,
                        dayOfWeek = dayOfWeek,
                        weekNumber = weekNum,
                        startPeriod = startPeriod,
                        endPeriod = endPeriod,
                        color = color,
                        courseGroupId = courseGroupId,
                        courseInstanceId = "${courseGroupId}_${weekNum}",
                        scheduleId = activeScheduleId
                    )
                )
            }
        }

        ParseImageResult(success = true, courses = courses, message = "识别到 ${courses.size} 条课程")
    } catch (e: Exception) {
        ParseImageResult(success = false, message = "解析失败: ${e.message}")
    }
}

/** 解析周次范围字符串，如 "1-16"、"1,3,5-10" */
private fun parseWeekRange(weekRange: String): List<Int> {
    val weeks = mutableListOf<Int>()
    val parts = weekRange.split(",")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val rangeParts = trimmed.split("-")
            if (rangeParts.size == 2) {
                val start = rangeParts[0].trim().toIntOrNull() ?: continue
                val end = rangeParts[1].trim().toIntOrNull() ?: continue
                for (w in start..end) weeks.add(w)
            }
        } else {
            val w = trimmed.toIntOrNull()
            if (w != null) weeks.add(w)
        }
    }
    return if (weeks.isEmpty()) listOf(1) else weeks.sorted()
}

/** 从Uri加载图片为Bitmap和ByteArray */
private fun loadImageFromUri(context: Context, uri: Uri, onLoaded: (Bitmap, ByteArray) -> Unit) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        // 压缩图片，避免base64后太大
        val resized = if (bitmap.width > 2048 || bitmap.height > 2048) {
            val scale = 2048f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()

        onLoaded(resized, bytes)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/** 创建图片Uri用于相机拍照 */
private fun createImageUri(context: Context): Uri {
    val timestamp = System.currentTimeMillis()
    val filename = "course_schedule_$timestamp.jpg"
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    }
    return context.contentResolver.insert(
        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ) ?: "".toUri()
}

data class ParseImageResult(
    val success: Boolean,
    val courses: List<Course> = emptyList(),
    val message: String = ""
)
