package com.example.a8319schedule.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.a8319schedule.data.AISettings
import com.example.a8319schedule.data.ModelPreset
import com.example.a8319schedule.data.ModelPresets
import com.example.a8319schedule.viewmodel.AIChatViewModel
import top.yukonga.miuix.kmp.basic.*
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    viewModel: AIChatViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChat: () -> Unit
) {
    val aiSettings by viewModel.aiSettings.collectAsState()
    val isConfigured by viewModel.isConfigured.collectAsState()
    
    var apiKey by remember { mutableStateOf(aiSettings.apiKey) }
    var baseUrl by remember { mutableStateOf(aiSettings.baseUrl) }
    var model by remember { mutableStateOf(aiSettings.model) }
    var enabled by remember { mutableStateOf(aiSettings.enabled) }
    var selectedPresetName by remember { mutableStateOf(aiSettings.presetName) }
    
    var showApiKey by remember { mutableStateOf(false) }
    var showPresetDropdown by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isCustomMode by remember { mutableStateOf(false) }
    
    LaunchedEffect(aiSettings) {
        apiKey = aiSettings.apiKey
        baseUrl = aiSettings.baseUrl
        model = aiSettings.model
        enabled = aiSettings.enabled
        selectedPresetName = aiSettings.presetName
        isCustomMode = selectedPresetName == "自定义" || (selectedPresetName.isEmpty() && (baseUrl.isNotEmpty() || model.isNotEmpty()))
    }
    
    fun selectPreset(preset: ModelPreset) {
        selectedPresetName = preset.name
        baseUrl = preset.baseUrl
        model = preset.defaultModel
        isCustomMode = preset.name == "自定义"
        showPresetDropdown = false
    }
    
    Scaffold(
        topBar = {
            HyperOSScreenTopBar(
                title = "AI助手设置",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 启用开关
            HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启用AI助手",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "开启后可在课表界面使用AI对话功能",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { 
                            enabled = it
                            viewModel.setEnabled(it)
                        }
                    )
                }
            }
            
            // 选择服务提供商
            HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "选择AI服务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "选择一个预设的AI服务，或选择自定义输入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // 服务选择下拉菜单
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (selectedPresetName.isEmpty()) "请选择..." else selectedPresetName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("服务提供商") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            ModelPresets.presets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(preset.name, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = preset.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = { selectPreset(preset) },
                                    leadingIcon = if (selectedPresetName == preset.name) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }
            
            // API配置
            HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "API配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("输入你的API密钥") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "隐藏" else "显示"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { 
                            baseUrl = it
                            isCustomMode = true
                            selectedPresetName = "自定义"
                        },
                        label = { Text("API地址") },
                        placeholder = { Text("https://api.example.com/v1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isCustomMode,
                        supportingText = { Text(if (isCustomMode) "请输入完整的API地址" else "已根据预设自动填充") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    OutlinedTextField(
                        value = model,
                        onValueChange = { 
                            model = it
                            isCustomMode = true
                            selectedPresetName = "自定义"
                        },
                        label = { Text("模型名称") },
                        placeholder = { Text("如：gpt-3.5-turbo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = isCustomMode,
                        supportingText = { Text(if (isCustomMode) "请输入模型名称" else "已根据预设自动填充") },
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Button(
                        onClick = {
                            viewModel.saveSettings(
                                AISettings(
                                    apiKey = apiKey,
                                    model = model,
                                    baseUrl = baseUrl,
                                    enabled = enabled,
                                    presetName = selectedPresetName
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存配置")
                    }
                }
            }
            
            // 快捷操作
            if (isConfigured) {
                HyperOSCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "快捷操作",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Button(
                            onClick = {
                                viewModel.saveSettings(
                                    AISettings(
                                        apiKey = apiKey,
                                        model = model,
                                        baseUrl = baseUrl,
                                        enabled = enabled,
                                        presetName = selectedPresetName
                                    )
                                )
                                onNavigateToChat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("打开AI对话")
                        }
                    }
                }
            }
            
            // 帮助说明
            HyperOSCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "支持的服务",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Text(
                        text = "• OpenAI (GPT-3.5/4/4o)\n" +
                               "• 硅基流动 (Qwen、GLM等)\n" +
                               "• 智谱AI (GLM-4)\n" +
                               "• 阿里云百炼 (通义千问)\n" +
                               "• 百度千帆 (文心一言)\n" +
                               "• 以及其他OpenAI兼容API",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "AI功能支持：",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• 添加新课程\n• 修改课程信息\n• 删除课程（单周或全部）\n• 批量操作同一课程",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // 危险操作
            TextButton(
                text = "重置对话",
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = TextButtonColors(
                    color = MaterialTheme.colorScheme.errorContainer,
                    textColor = MaterialTheme.colorScheme.error,
                    disabledColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    disabledTextColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                )
            )
        }
    }
    
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("确认重置", fontWeight = FontWeight.SemiBold) },
            text = { Text("确定要清除所有AI设置吗？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    text = "确认重置",
                    onClick = {
                        viewModel.saveSettings(AISettings())
                        apiKey = ""
                        baseUrl = ""
                        model = ""
                        enabled = false
                        selectedPresetName = ""
                        isCustomMode = false
                        showResetDialog = false
                    },
                    colors = TextButtonColors(
                        color = Color.Transparent,
                        textColor = MaterialTheme.colorScheme.error,
                        disabledColor = Color.Transparent,
                        disabledTextColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                )
            },
            dismissButton = {
                TextButton(
                    text = "取消",
                    onClick = { showResetDialog = false },
                    colors = TextButtonColors(
                        color = Color.Transparent,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        disabledColor = Color.Transparent,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                )
            }
        )
    }
}


