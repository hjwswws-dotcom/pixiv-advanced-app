package com.paf.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.paf.app.data.api.PixivApiService
import com.paf.app.data.model.SearchConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    
    // 状态
    var keyword by remember { mutableStateOf("") }
    var minPages by remember { mutableStateOf("2") }
    var startPage by remember { mutableStateOf("1") }
    var endPage by remember { mutableStateOf("-1") }
    var targetCount by remember { mutableStateOf("20") }
    var batchSize by remember { mutableStateOf("10") }
    var aiMode by remember { mutableStateOf("allow") }
    var r18Mode by remember { mutableStateOf("allow") }
    
    var showLoginWebView by remember { mutableStateOf(false) }
    
    // 登录状态：使用 mutableStateOf 以便手动更新
    var isLoggedIn by remember { mutableStateOf(PixivApiService.cookies.isNotEmpty()) }
    
    // 当外部cookies变化时刷新状态
    LaunchedEffect(PixivApiService.cookies) {
        isLoggedIn = PixivApiService.cookies.isNotEmpty()
        Log.d("PAF_Login", "Cookie changed, isLoggedIn: $isLoggedIn")
    }
    
    var currentPage by remember { mutableStateOf(1) }
    
    // 底部导航：0=首页，1=收集页，2=历史，3=设置
    var selectedTab by remember { mutableStateOf(0) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PAF - Pixiv高级过滤器") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🔍") },
                    label = { Text("搜索") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("📊") },
                    label = { Text("收集") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("📁") },
                    label = { Text("历史") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Text("⚙️") },
                    label = { Text("设置") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> SearchTab(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                minPages = minPages,
                onMinPagesChange = { minPages = it },
                startPage = startPage,
                onStartPageChange = { startPage = it },
                endPage = endPage,
                onEndPageChange = { endPage = it },
                targetCount = targetCount,
                onTargetCountChange = { targetCount = it },
                batchSize = batchSize,
                onBatchSizeChange = { batchSize = it },
                aiMode = aiMode,
                onAIModeChange = { aiMode = it },
                r18Mode = r18Mode,
                onR18ModeChange = { r18Mode = it },
                isLoggedIn = isLoggedIn,
                onLoginClick = { showLoginWebView = true },
                onStartClick = {
                    selectedTab = 1
                },
                modifier = Modifier.padding(paddingValues)
            )
            1 -> CollectingScreen(
                config = SearchConfig(
                    keyword = keyword,
                    minPages = minPages.toIntOrNull() ?: 2,
                    startPage = startPage.toIntOrNull() ?: 1,
                    endPage = endPage.toIntOrNull() ?: -1,
                    targetCount = targetCount.toIntOrNull() ?: 20,
                    batchSize = batchSize.toIntOrNull() ?: 10,
                    aiMode = aiMode,
                    r18Mode = r18Mode
                ),
                modifier = Modifier.padding(paddingValues)
            )
            2 -> HistoryScreen(modifier = Modifier.padding(paddingValues))
            3 -> SettingsScreen(
                isLoggedIn = isLoggedIn,
                onLogout = {
                    PixivApiService.cookies = ""
                    CookieManager.getInstance().removeAllCookies(null)
                    isLoggedIn = false
                },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
    
    // 登录 WebView
    val scope = rememberCoroutineScope()
    
    if (showLoginWebView) {
        LoginWebView(
            onCookiesReceived = { cookies ->
                // 记录原始 Cookie
                Log.d("PAF_Login", "Received cookies: ${cookies.length} chars")
                
                // 设置 Cookie 到 API 层
                PixivApiService.cookies = cookies
                Log.d("PAF_Login", "Synced to API service: ${PixivApiService.cookies.length} chars")
                
                // 验证登录状态
                scope.launch {
                    val apiService = PixivApiService()
                    val verifyResult = apiService.verifyLogin()
                    if (verifyResult.success) {
                        Log.d("PAF_Login", "Login verified: ${verifyResult.userName}")
                    } else {
                        Log.w("PAF_Login", "Login verify failed: ${verifyResult.error}")
                    }
                    // 验证后刷新状态
                    isLoggedIn
                    showLoginWebView = false
                }
            },
            onDismiss = { showLoginWebView = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTab(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    minPages: String,
    onMinPagesChange: (String) -> Unit,
    startPage: String,
    onStartPageChange: (String) -> Unit,
    endPage: String,
    onEndPageChange: (String) -> Unit,
    targetCount: String,
    onTargetCountChange: (String) -> Unit,
    batchSize: String,
    onBatchSizeChange: (String) -> Unit,
    aiMode: String,
    onAIModeChange: (String) -> Unit,
    r18Mode: String,
    onR18ModeChange: (String) -> Unit,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 登录状态
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isLoggedIn) 
                    MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLoggedIn) "✅ 已登录 Pixiv" else "❌ 未登录",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(onClick = onLoginClick) {
                    Text(if (isLoggedIn) "重新登录" else "登录")
                }
            }
        }
        
        // 关键词
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            label = { Text("关键词") },
            placeholder = { Text("例如：風景") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // 最少图片数
        OutlinedTextField(
            value = minPages,
            onValueChange = onMinPagesChange,
            label = { Text("最少图片数") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        
        // 起始页 / 终止页
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = startPage,
                onValueChange = onStartPageChange,
                label = { Text("起始页") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = endPage,
                onValueChange = onEndPageChange,
                label = { Text("终止页 (-1=无限)") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
        
        // 目标作品数
        OutlinedTextField(
            value = targetCount,
            onValueChange = onTargetCountChange,
            label = { Text("目标作品数") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        
        // 每批处理页数
        OutlinedTextField(
            value = batchSize,
            onValueChange = onBatchSizeChange,
            label = { Text("每批处理页数") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        
        // AI 过滤
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI过滤：", modifier = Modifier.width(80.dp))
            FilterChip(
                selected = aiMode == "allow",
                onClick = { onAIModeChange("allow") },
                label = { Text("不过滤") }
            )
            FilterChip(
                selected = aiMode == "none",
                onClick = { onAIModeChange("none") },
                label = { Text("排除AI") }
            )
            FilterChip(
                selected = aiMode == "ai",
                onClick = { onAIModeChange("ai") },
                label = { Text("仅AI") }
            )
        }
        
        // R18 过滤
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("R18过滤：", modifier = Modifier.width(80.dp))
            FilterChip(
                selected = r18Mode == "allow",
                onClick = { onR18ModeChange("allow") },
                label = { Text("不过滤") }
            )
            FilterChip(
                selected = r18Mode == "none",
                onClick = { onR18ModeChange("none") },
                label = { Text("排除R18") }
            )
            FilterChip(
                selected = r18Mode == "r18",
                onClick = { onR18ModeChange("r18") },
                label = { Text("仅R18") }
            )
        }
        
        // 开始按钮
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = keyword.isNotBlank() && isLoggedIn
        ) {
            Text("开始收集", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun LoginWebView(
    onCookiesReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var debugLog by remember { mutableStateOf("") }
    
    // 辅助函数：检查多个域名的 Cookie
    fun checkAndExtractCookies(): String? {
        val cookieManager = CookieManager.getInstance()
        
        // 检查多个域名
        val domains = listOf(
            "https://www.pixiv.net",
            "https://accounts.pixiv.net"
        )
        
        var allCookies = ""
        var cookieDetails = ""
        
        for (domain in domains) {
            val cookies = cookieManager.getCookie(domain)
            if (!cookies.isNullOrEmpty()) {
                allCookies = cookies
                // 解析 Cookie 键名
                val cookieNames = cookies.split(";").map { it.trim().split("=")[0] }
                cookieDetails += "$domain: ${cookieNames.size} cookies\n"
                cookieDetails += "Keys: ${cookieNames.joinToString(", ")}\n"
                cookieDetails += "Length: ${cookies.length}\n\n"
            }
        }
        
        debugLog = "=== Cookie Debug ===\n$cookieDetails"
        
        // 检查是否包含登录凭证
        val hasLoginCookie = allCookies.contains("PHPSESSID") || 
                            allCookies.contains("user_id") ||
                            allCookies.contains("pixiv_ces")
        
        return if (hasLoginCookie && allCookies.isNotEmpty()) allCookies else null
    }

    AlertDialog(
        onDismissRequest = { 
            // 关闭前再次检测
            checkAndExtractCookies()?.let { onCookiesReceived(it) }
            onDismiss() 
        },
        title = { Text("Pixiv 登录") },
        text = {
            Column {
                Text(
                    "请在下方点击右上角「登录」按钮，完成登录后自动关闭",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                AndroidView(
                    factory = { ctx ->
                        // 启用 Cookie
                        CookieManager.getInstance().setAcceptCookie(true)
                        
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowContentAccess = true
                            settings.allowFileAccess = true
                            
                            // 启用第三方 Cookie
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            var lastUrl = ""
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    // 页面开始加载时检测
                                    checkAndExtractCookies()?.let { 
                                        Log.d("PAF_Login", "Cookie found on page start: ${it.length} chars")
                                        onCookiesReceived(it)
                                    }
                                }
                                
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    
                                    // 检测当前页面 Cookie
                                    val cookies = checkAndExtractCookies()
                                    if (cookies != null) {
                                        Log.d("PAF_Login", "Cookie found on page finish: ${cookies.length} chars")
                                        onCookiesReceived(cookies)
                                    }
                                    
                                    // 记录 URL 变化
                                    if (url != null && url != lastUrl) {
                                        Log.d("PAF_Login", "URL changed to: $url")
                                        lastUrl = url
                                        
                                        // 关键 URL 跳转后检测
                                        if (url.contains("pixiv.net") || url.contains("account")) {
                                            checkAndExtractCookies()?.let {
                                                onCookiesReceived(it)
                                            }
                                        }
                                    }
                                }
                                
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    // URL 跳转前检测
                                    checkAndExtractCookies()?.let { 
                                        onCookiesReceived(it)
                                    }
                                    return false
                                }
                            }

                            loadUrl("https://www.pixiv.net/")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                )
                
                // 调试信息（生产环境可移除）
                if (debugLog.isNotEmpty()) {
                    Text(
                        text = debugLog,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                // 关闭前最后一次检测
                checkAndExtractCookies()?.let { onCookiesReceived(it) }
                onDismiss() 
            }) {
                Text("关闭")
            }
        }
    )
}
