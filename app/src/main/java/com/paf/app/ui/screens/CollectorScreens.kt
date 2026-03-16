package com.paf.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.paf.app.data.api.PixivApiService
import com.paf.app.data.model.SearchConfig
import com.paf.app.data.model.TaskState
import com.paf.app.data.repository.SearchHistoryRepository
import com.paf.app.domain.CollectorEngine
import com.paf.app.domain.CollectorState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CollectingScreen(
    config: SearchConfig,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apiService = remember { PixivApiService() }
    val repository = remember { SearchHistoryRepository(context) }
    val collectorEngine = remember { CollectorEngine(apiService, repository) }
    
    var state by remember { mutableStateOf(CollectorState()) }
    
    LaunchedEffect(Unit) {
        collectorEngine.state.collectLatest { s ->
            state = s
        }
    }
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 状态卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 状态
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "状态：${state.state.name}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    when (state.state) {
                        TaskState.RUNNING -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        TaskState.PAUSED -> Text("⏸")
                        TaskState.COMPLETED -> Text("✅")
                        TaskState.ERROR -> Text("❌")
                        else -> {}
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 关键词
                Text("关键词：${config.keyword}", style = MaterialTheme.typography.bodyMedium)
                Text("筛选：最少${config.minPages}张 | 页数范围：${config.startPage} ~ ${if (config.endPage > 0) config.endPage else "∞"}", style = MaterialTheme.typography.bodySmall)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 进度
                val progress = if (config.endPage > 0) {
                    (state.currentPage - config.startPage).toFloat() / (config.endPage - config.startPage + 1)
                } else {
                    state.matchedCount.toFloat() / config.targetCount
                }
                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 统计
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.currentPage}", style = MaterialTheme.typography.headlineMedium)
                        Text("当前页", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.pagesVisited}", style = MaterialTheme.typography.headlineMedium)
                        Text("已扫描", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.matchedCount}", style = MaterialTheme.typography.headlineMedium)
                        Text("已命中", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${config.targetCount}", style = MaterialTheme.typography.headlineMedium)
                        Text("目标", style = MaterialTheme.typography.bodySmall)
                    }
                }
                
                // 错误信息
                state.lastError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "错误：$error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                // 调试信息（结果为0时显示）
                if (state.debugInfo.isNotEmpty() && state.matchedCount == 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "🔍 调试信息",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.debugInfo,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
        
        // 控制按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!state.isActivelyRunning && state.state == TaskState.IDLE) {
                Button(
                    onClick = { collectorEngine.start(config) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("▶ 开始")
                }
            } else if (state.isActivelyRunning) {
                Button(
                    onClick = { collectorEngine.pause() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("⏸ 暂停")
                }
            } else if (state.isPaused) {
                Button(
                    onClick = { collectorEngine.resume() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("▶ 继续")
                }
                Button(
                    onClick = { collectorEngine.stop() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("⏹ 停止")
                }
            }
        }
        
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        
        // 结果列表
        Text("结果列表 (${state.results.size})", modifier = Modifier.padding(horizontal = 16.dp))
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.results) { artwork ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 打开系统浏览器
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(artwork.url))
                            context.startActivity(intent)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = artwork.thumbnail,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artwork.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Text(
                                text = artwork.author,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                Text(
                                    text = "${artwork.pageCount}张",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (artwork.isAI) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "AI",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9C27B0)
                                    )
                                }
                                if (artwork.r18 != "none") {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = artwork.r18.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFF44336)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { SearchHistoryRepository(context) }
    val history by repository.getSearchHistory().collectAsState(initial = emptyList())
    
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            "历史搜索 (${history.size})",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        
        if (history.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无历史记录", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = item.config.keyword,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "筛选: ${item.config.minPages}张 | 结果: ${item.resultCount}个",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    isLoggedIn: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pixiv 登录状态", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(if (isLoggedIn) "已登录" else "未登录")
                
                if (isLoggedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("退出登录")
                    }
                }
            }
        }
        
        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Text("PAF - Pixiv高级过滤器")
                Text("版本：1.0.0")
            }
        }
    }
}
