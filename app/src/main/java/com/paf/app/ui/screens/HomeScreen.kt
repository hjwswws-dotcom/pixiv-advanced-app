package com.paf.app.ui.screens

import android.content.Intent
import android.net.Uri
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
    var excludeAI by remember { mutableStateOf(false) }
    var r18Mode by remember { mutableStateOf("allow") }
    
    var showLoginWebView by remember { mutableStateOf(false) }
    var isLoggedIn by remember { mutableStateOf(PixivApiService.cookies.isNotEmpty()) }
    
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
                excludeAI = excludeAI,
                onExcludeAIChange = { excludeAI = it },
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
                    excludeAI = excludeAI,
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
    if (showLoginWebView) {
        LoginWebView(
            onCookiesReceived = { cookies ->
                PixivApiService.cookies = cookies
                isLoggedIn = true
                showLoginWebView = false
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
    excludeAI: Boolean,
    onExcludeAIChange: (Boolean) -> Unit,
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
            Checkbox(
                checked = excludeAI,
                onCheckedChange = onExcludeAIChange
            )
            Text("排除AI作品", modifier = Modifier.padding(start = 8.dp))
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
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pixiv 登录") },
        text = {
            Column {
                Text("请在下方点击右上角「登录」按钮，完成登录后自动关闭", 
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp))
                AndroidView(
                    factory = {
                        WebView(it).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // 获取 Cookie
                                val cookieManager = CookieManager.getInstance()
                                val cookies = cookieManager.getCookie("pixiv.net")
                                // 检查是否包含登录凭证（PHPSESSID 或 user_id）
                                if (cookies != null && (cookies.contains("PHPSESSID") || cookies.contains("user_id"))) {
                                    onCookiesReceived(cookies)
                                }
                            }
                        }
                        
                        loadUrl("https://www.pixiv.net/")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
