package com.example.a8319schedule.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

// 默认教务系统地址（江西理工大学，学期理论课表页，未登录时强智会自动跳转登录页）
private const val DEFAULT_URL = "https://jw.jxust.edu.cn/jsxsd/xskb/xskb_list.do"

// 桌面模式的User Agent
private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

/**
 * 导入模式：决定 ✓ 按钮抓取哪一类信息。
 * - SCHEDULE：原课表流程（加载课表页 + 解析课表 + 顺带抓全部）
 * - 其它：不碰课表，仅 fetch 对应接口并回传 HTML
 */
enum class ImportMode(val displayName: String) {
    SCHEDULE("课表"),
    EXAM("考试安排"),
    SCORE("成绩"),
    TRAINING_PLAN("培养方案(执行计划)"),
    TRAINING_PLAN_ALL("培养方案(课程设置总表)")
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewImportScreen(
    onNavigateBack: () -> Unit,
    onImportSuccess: (String) -> Unit,
    initialUrl: String = DEFAULT_URL,
    parserType: String = "jxust",
    importMode: ImportMode = ImportMode.SCHEDULE
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var url by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("登录教务系统") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDesktopMode by remember { mutableStateOf(false) }
    
    val isProcessing = remember { AtomicBoolean(false) }
    // 标记点击✓后等待自动解析
    val pendingParse = remember { mutableStateOf(false) }
    // 标记点击✓后等待自动抓取其它信息（考试/成绩/培养方案）
    val pendingOtherInfo = remember { mutableStateOf<ImportMode?>(null) }
    
    // 加载超时检测
    LaunchedEffect(isLoading) {
        if (isLoading) {
            delay(10000)
            if (isLoading) {
                isLoading = false
                Toast.makeText(context, "页面加载超时，已停止加载", Toast.LENGTH_LONG).show()
                webViewRef?.stopLoading()
            }
        }
    }
    
    // 加载JavaScript解析器
    val loadScheduleParser = remember {
        {
            try {
                val jsFileName = "schedule_parser.js"
                
                Log.d("WebViewImport", "加载解析器: $jsFileName")
                
                val jsCode = context.assets.open(jsFileName).use { inputStream ->
                    val size = inputStream.available()
                    val buffer = ByteArray(size)
                    inputStream.read(buffer)
                    String(buffer, Charsets.UTF_8)
                }
                
                webViewRef?.evaluateJavascript(jsCode) {
                    val bridgeJs = """
                        (function() {
                            if (!window.bridgeCallCount) {
                                window.bridgeCallCount = 0;
                                window.isProcessing = false;
                            }

                            // 计算当前学年学期标识，如 "2025-2026-2"
                            window.getCurrentXnxqid = function() {
                                var now = new Date();
                                var year = now.getFullYear();
                                var month = now.getMonth() + 1;
                                if (month >= 9) return year + '-' + (year + 1) + '-1';
                                if (month >= 2 && month <= 7) return (year - 1) + '-' + year + '-2';
                                if (month === 1) return (year - 1) + '-' + year + '-1';
                                return year + '-' + (year + 1) + '-1';
                            };

                            // 抓取考试安排：POST 学期参数，提取 #dataList 表格
                            window.fetchExamHtml = async function() {
                                var xnxqid = window.getCurrentXnxqid();
                                try {
                                    var resp = await fetch('/jsxsd/xsks/xsksap_list', {
                                        method: 'POST',
                                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                        body: 'xqlbmc=&xnxqid=' + encodeURIComponent(xnxqid)
                                    });
                                    var html = await resp.text();
                                    var div = document.createElement('div');
                                    div.innerHTML = html;
                                    var table = div.querySelector('#dataList');
                                    return {xnxqid: xnxqid, examHtml: table ? table.outerHTML : '', error: null};
                                } catch (e) {
                                    return {xnxqid: xnxqid, examHtml: '', error: e.message};
                                }
                            };

                            // 抓取培养方案（执行计划）：GET 无参数，提取 #dataList 表格
                            window.fetchTrainingPlanHtml = async function() {
                                try {
                                    var resp = await fetch('/jsxsd/pyfa/pyfa_query', { method: 'GET' });
                                    var html = await resp.text();
                                    var div = document.createElement('div');
                                    div.innerHTML = html;
                                    var table = div.querySelector('#dataList');
                                    return { planHtml: table ? table.outerHTML : '', error: null };
                                } catch (e) {
                                    return { planHtml: '', error: e.message };
                                }
                            };

                            // 抓取培养方案及完成情况（课程设置总表）：GET 无参数，提取 #mxh 表格
                            window.fetchAllPlansHtml = async function() {
                                try {
                                    var resp = await fetch('/jsxsd/pyfa/topyfamx', { method: 'GET' });
                                    var html = await resp.text();
                                    var div = document.createElement('div');
                                    div.innerHTML = html;
                                    var table = div.querySelector('#mxh');
                                    return { allPlanHtml: table ? table.outerHTML : '', error: null };
                                } catch (e) {
                                    return { allPlanHtml: '', error: e.message };
                                }
                            };

                            // 抓取成绩查询：POST 查询全部（空参数），提取 #dataList 表格
                            window.fetchScoreHtml = async function() {
                                try {
                                    var resp = await fetch('/jsxsd/kscj/cjcx_list', {
                                        method: 'POST',
                                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                        body: 'cj0701id=&zc=&kcdm=&kclb='
                                    });
                                    var html = await resp.text();
                                    var div = document.createElement('div');
                                    div.innerHTML = html;
                                    var table = div.querySelector('#dataList');
                                    return { scoreHtml: table ? table.outerHTML : '', error: null };
                                } catch (e) {
                                    return { scoreHtml: '', error: e.message };
                                }
                            };

                            // 单独抓取某类信息（非课表模式用），通过 onOtherInfoParsed 回传
                            window.fetchAndSendOtherInfo = async function(mode) {
                                window.bridgeCallCount++;
                                if (window.AndroidBridge && !window.isProcessing) {
                                    window.isProcessing = true;
                                    try {
                                        var result = { mode: mode, html: '', xnxqid: '', error: null };
                                        if (mode === 'EXAM') {
                                            var examInfo = await window.fetchExamHtml();
                                            result.html = examInfo.examHtml;
                                            result.xnxqid = examInfo.xnxqid;
                                            result.error = examInfo.error;
                                        } else if (mode === 'SCORE') {
                                            var scoreInfo = await window.fetchScoreHtml();
                                            result.html = scoreInfo.scoreHtml;
                                            result.error = scoreInfo.error;
                                        } else if (mode === 'TRAINING_PLAN') {
                                            var planInfo = await window.fetchTrainingPlanHtml();
                                            result.html = planInfo.planHtml;
                                            result.error = planInfo.error;
                                        } else if (mode === 'TRAINING_PLAN_ALL') {
                                            var allPlanInfo = await window.fetchAllPlansHtml();
                                            result.html = allPlanInfo.allPlanHtml;
                                            result.error = allPlanInfo.error;
                                        }
                                        window.AndroidBridge.onOtherInfoParsed(JSON.stringify(result));
                                    } catch (e) {
                                        console.error("[8319课表助手] 抓取" + mode + "出错:", e);
                                        window.AndroidBridge.onOtherInfoParsed(JSON.stringify({ mode: mode, html: '', xnxqid: '', error: e.message }));
                                    }
                                    setTimeout(() => {
                                        window.isProcessing = false;
                                    }, 3000);
                                }
                            };

                            // 把课表 JSON 与考试 HTML、培养方案 HTML 合并后回传
                            window.sendScheduleToAndroid = async function(coursesJson, debugInfo) {
                                window.bridgeCallCount++;
                                if (window.AndroidBridge && !window.isProcessing) {
                                    window.isProcessing = true;
                                    try {
                                        if (coursesJson && coursesJson.trim() !== '') {
                                            // 课表解析成功：顺带抓取考试安排、培养方案和成绩并合并
                                            var examInfo = await window.fetchExamHtml();
                                            var planInfo = await window.fetchTrainingPlanHtml();
                                            var allPlanInfo = await window.fetchAllPlansHtml();
                                            var scoreInfo = await window.fetchScoreHtml();
                                            try {
                                                var obj = JSON.parse(coursesJson);
                                                obj.examHtml = examInfo.examHtml;
                                                obj.xnxqid = examInfo.xnxqid;
                                                obj.examError = examInfo.error;
                                                obj.planHtml = planInfo.planHtml;
                                                obj.planError = planInfo.error;
                                                obj.allPlanHtml = allPlanInfo.allPlanHtml;
                                                obj.allPlanError = allPlanInfo.error;
                                                obj.scoreHtml = scoreInfo.scoreHtml;
                                                obj.scoreError = scoreInfo.error;
                                                window.AndroidBridge.onScheduleParsed(JSON.stringify(obj), null);
                                            } catch (e) {
                                                window.AndroidBridge.onScheduleParsed(coursesJson, null);
                                            }
                                        } else {
                                            // 课表解析失败：保持原有失败处理
                                            window.AndroidBridge.onScheduleParsed(null, debugInfo || "解析结果为空");
                                        }
                                    } catch (e) {
                                        console.error("[8319课表助手] 处理错误:", e);
                                        window.AndroidBridge.onScheduleParsed(null, "错误: " + e.message);
                                    }
                                    setTimeout(() => {
                                        window.isProcessing = false;
                                    }, 3000);
                                }
                            };

                            const originalExtractSchedule = window.extractSchedule;
                            window.extractSchedule = function() {
                                try {
                                    const result = originalExtractSchedule();
                                    sendScheduleToAndroid(result, null);
                                    return result;
                                } catch (e) {
                                    console.error("[8319课表助手] 解析课表时出错:", e);
                                    sendScheduleToAndroid(null, e.message);
                                    return "错误: " + e.message;
                                }
                            };

                            console.log("[8319课表助手] JavaScript解析器和桥接函数已加载");
                        })();
                    """.trimIndent()
                    
                    webViewRef?.evaluateJavascript(bridgeJs, null)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "加载解析器失败: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("WebViewImport", "加载JavaScript解析器失败", e)
            }
        }
    }
    
    val resetWebView = {
        webViewRef?.clearCache(true)
        webViewRef?.clearHistory()
        webViewRef?.clearFormData()
        isProcessing.set(false)
        
        if (url != initialUrl) {
            url = initialUrl
        }
        webViewRef?.stopLoading()
        webViewRef?.loadUrl(initialUrl)
    }
    
    val toggleDesktopMode = {
        isDesktopMode = !isDesktopMode
        webViewRef?.settings?.apply {
            userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(context)
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        
        Toast.makeText(context, "已切换到${if (isDesktopMode) "桌面" else "移动"}版模式", Toast.LENGTH_SHORT).show()
        
        val currentUrl = webViewRef?.url ?: DEFAULT_URL
        if (currentUrl.isNotEmpty() && currentUrl != "about:blank") {
            webViewRef?.loadUrl(currentUrl)
        }
    }
    
    val parseSchedule = {
        isProcessing.set(false)
        
        webViewRef?.evaluateJavascript("""
            (function() {
                window.isProcessing = false;
                window.bridgeCallCount = 0;
                
                setTimeout(function() {
                    if (window.extractSchedule) {
                        console.log("[8319课表助手] 开始执行课表解析");
                        try {
                            const result = window.extractSchedule();
                            console.log("[8319课表助手] 解析结果:", result);
                            if (result && typeof result === 'string') {
                                const parsedResult = JSON.parse(result);
                                if (parsedResult.success && parsedResult.schedule && parsedResult.schedule.length > 0) {
                                    window.sendScheduleToAndroid(result, null);
                                } else {
                                    window.sendScheduleToAndroid(null, parsedResult.error || "未找到课程数据");
                                }
                            } else {
                                window.sendScheduleToAndroid(null, "解析结果格式错误");
                            }
                        } catch (e) {
                            console.error("[8319课表助手] 解析出错:", e);
                            window.sendScheduleToAndroid(null, e.message);
                        }
                    } else {
                        console.log("[8319课表助手] 解析函数未加载，尝试重新加载");
                        window.loadScheduleParser && window.loadScheduleParser();
                    }
                }, 1000);
            })();
        """.trimIndent(), null)
        
        Toast.makeText(context, "正在解析课表...", Toast.LENGTH_SHORT).show()
    }
    
    // HyperOS 风格的背景和配色
    val hyperBackground = Color(0xFFF5F5F5)
    val hyperPrimary = Color(0xFF1A73E8)
    val hyperSurface = Color.White
    val hyperOnSurface = Color(0xFF1F1F1F)
    val hyperOnSurfaceVariant = Color(0xFF757575)
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(hyperBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部导航栏 - HyperOS 风格
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = hyperSurface,
                tonalElevation = 0.dp,
                shadowElevation = if (isLoading) 0.dp else 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = hyperOnSurface
                        )
                    }
                    
                    Text(
                        text = pageTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = hyperOnSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    IconButton(onClick = { resetWebView() }) {
                        Icon(Icons.Default.Refresh, "重新加载", tint = hyperOnSurfaceVariant)
                    }
                    IconButton(onClick = { toggleDesktopMode() }) {
                        Icon(Icons.Default.Info, if (isDesktopMode) "桌面版" else "移动版", tint = hyperOnSurfaceVariant)
                    }
                    IconButton(onClick = {
                        if (importMode == ImportMode.SCHEDULE) {
                            // 课表模式：点击✓后自动加载课表页，加载完成后自动解析
                            pendingParse.value = true
                            webViewRef?.loadUrl(DEFAULT_URL)
                            Toast.makeText(context, "正在获取课表...", Toast.LENGTH_SHORT).show()
                        } else {
                            // 其它模式：和课表模式一样，重新加载课表页确保 bridge 就绪后自动抓取
                            pendingOtherInfo.value = importMode
                            isProcessing.set(false)
                            webViewRef?.loadUrl(DEFAULT_URL)
                            Toast.makeText(context, "正在获取${importMode.displayName}...", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Check, "导入${importMode.displayName}", tint = hyperPrimary)
                    }
                }
            }
            
            // URL 输入框 - HyperOS 风格
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = hyperSurface,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            color = hyperOnSurface
                        ),
                        cursorBrush = SolidColor(hyperPrimary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (url.isEmpty()) {
                                    Text(
                                        "输入教务系统地址",
                                        fontSize = 14.sp,
                                        color = hyperOnSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                    
                    TextButton(onClick = { 
                        webViewRef?.stopLoading()
                        webViewRef?.clearCache(true)
                        webViewRef?.clearHistory()
                        webViewRef?.clearFormData()
                        
                        var targetUrl = url.trim()
                        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
                            targetUrl = "https://$targetUrl"
                        }
                        
                        webViewRef?.loadUrl(targetUrl)
                    }) {
                        Text("访问", color = hyperPrimary, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            // 提示文字
            Text(
                if (importMode == ImportMode.SCHEDULE) "登录后点击右上角 ✓ 自动导入课表"
                else "登录后点击右上角 ✓ 导入${importMode.displayName}",
                fontSize = 12.sp,
                color = hyperOnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                            javaScriptCanOpenWindowsAutomatically = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccessFromFileURLs = true
                            allowUniversalAccessFromFileURLs = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                
                                Handler(Looper.getMainLooper()).postDelayed({
                                    addJavascriptInterface(object {
                                        @JavascriptInterface
                                        fun onScheduleParsed(coursesJson: String?, debugInfoParam: String?) {
                                            if (!isProcessing.getAndSet(true)) {
                                                scope.launch {
                                                    if (coursesJson != null && coursesJson.isNotEmpty()) {
                                                        if (coursesJson.startsWith("错误:")) {
                                                            val errorMsg = coursesJson.substringAfter("错误: ")
                                                            Toast.makeText(context, "课表解析失败: $errorMsg", Toast.LENGTH_LONG).show()
                                                        } else {
                                                            onImportSuccess(coursesJson)
                                                            Toast.makeText(context, "课表解析成功", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        val errorMsg = debugInfoParam ?: "未知错误"
                                                        Toast.makeText(context, "课表解析失败: $errorMsg", Toast.LENGTH_LONG).show()
                                                    }
                                                    
                                                    delay(3000)
                                                    isProcessing.set(false)
                                                }
                                            }
                                        }

                                        @JavascriptInterface
                                        fun onOtherInfoParsed(json: String?) {
                                            if (!isProcessing.getAndSet(true)) {
                                                scope.launch {
                                                    if (json != null && json.isNotEmpty()) {
                                                        onImportSuccess(json)
                                                        Toast.makeText(context, "获取成功", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "获取失败: 未收到数据", Toast.LENGTH_LONG).show()
                                                    }
                                                    delay(3000)
                                                    isProcessing.set(false)
                                                }
                                            }
                                        }
                                    }, "AndroidBridge")
                                    
                                    loadScheduleParser()

                                    // 点击✓后自动解析：检测当前是否在课表页
                                    if (pendingParse.value) {
                                        val currentUrl = view?.url ?: ""
                                        pendingParse.value = false
                                        if (currentUrl.contains("xskb")) {
                                            // 已在课表页，自动执行解析
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                parseSchedule()
                                            }, 1500)
                                        } else {
                                            // 未在课表页（可能跳转到了登录页）
                                            Toast.makeText(context, "未检测到课表页面，请先登录", Toast.LENGTH_LONG).show()
                                        }
                                    }

                                    // 点击✓后自动抓取其它信息（考试/成绩/培养方案）
                                    if (pendingOtherInfo.value != null) {
                                        val mode = pendingOtherInfo.value!!
                                        pendingOtherInfo.value = null
                                        val currentUrl = view?.url ?: ""
                                        if (currentUrl.contains("xskb")) {
                                            // 已登录，延迟后自动抓取对应信息
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                val modeName = mode.name
                                                webViewRef?.evaluateJavascript(
                                                    "(function(){ if(window.fetchAndSendOtherInfo){ window.fetchAndSendOtherInfo('$modeName'); } else { window.AndroidBridge && window.AndroidBridge.onOtherInfoParsed(JSON.stringify({mode:'$modeName',html:'',xnxqid:'',error:'解析器未加载，请重试'})); } })();",
                                                    null
                                                )
                                            }, 1500)
                                        } else {
                                            // 未在课表页（可能跳转到了登录页）
                                            Toast.makeText(context, "未检测到课表页面，请先登录", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }, 1000)
                            }
                            
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.cancel()
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title ?: "教务系统"
                            }
                        }
                        
                        loadUrl(initialUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            
            // 加载指示器 - HyperOS 风格
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = hyperPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
