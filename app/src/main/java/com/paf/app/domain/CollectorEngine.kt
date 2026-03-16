package com.paf.app.domain

import com.paf.app.data.api.PixivApiService
import com.paf.app.data.model.Artwork
import com.paf.app.data.model.SearchConfig
import com.paf.app.data.model.TaskState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * 收集器引擎
 * 核心业务逻辑
 */
class CollectorEngine(
    private val apiService: PixivApiService
) {
    
    private val _state = MutableStateFlow(CollectorState())
    val state: StateFlow<CollectorState> = _state.asStateFlow()
    
    private var collectJob: Job? = null
    private val seenIds = mutableSetOf<Long>()
    
    /**
     * 开始收集
     */
    fun start(config: SearchConfig) {
        if (_state.value.isActivelyRunning) return
        
        seenIds.clear()
        _state.value = CollectorState(
            isRunning = true,
            isPaused = false,
            config = config,
            currentPage = config.startPage,
            state = TaskState.RUNNING
        )
        
        collectJob = CoroutineScope(Dispatchers.Default).launch {
            collect()
        }
    }
    
    /**
     * 暂停
     */
    fun pause() {
        collectJob?.cancel()
        _state.update { it.copy(isPaused = true, state = TaskState.PAUSED) }
    }
    
    /**
     * 继续
     */
    fun resume() {
        if (_state.value.isPaused) {
            _state.update { it.copy(isPaused = false, state = TaskState.RUNNING) }
            collectJob = CoroutineScope(Dispatchers.Default).launch {
                collect()
            }
        }
    }
    
    /**
     * 停止
     */
    fun stop() {
        collectJob?.cancel()
        _state.update { it.copy(isRunning = false, state = TaskState.STOPPED) }
    }
    
    /**
     * 核心收集循环
     */
    private suspend fun collect() {
        val config = _state.value.config ?: return
        
        while (_state.value.isActivelyRunning) {
            val currentPage = _state.value.currentPage
            
            // 检查是否超过终止页
            if (config.endPage > 0 && currentPage > config.endPage) {
                _state.update { it.copy(isRunning = false, state = TaskState.COMPLETED) }
                break
            }
            
            // 检查是否达到目标数量
            if (_state.value.matchedCount >= config.targetCount) {
                _state.update { it.copy(isRunning = false, state = TaskState.COMPLETED) }
                break
            }
            
            // 获取搜索结果
            val result = apiService.search(config.keyword, currentPage)
            
            if (!result.success) {
                _state.update { 
                    it.copy(
                        lastError = result.error,
                        consecutiveErrors = it.consecutiveErrors + 1
                    )
                }
                
                // 连续错误达到阈值，停止
                if (_state.value.consecutiveErrors >= 5) {
                    _state.update { it.copy(isRunning = false, state = TaskState.ERROR) }
                    break
                }
                
                delay(2000)
                continue
            }
            
            // 重置连续错误计数
            _state.update { it.copy(consecutiveErrors = 0) }
            
            // 更新已访问页数
            _state.update { 
                it.copy(pagesVisited = it.pagesVisited + 1) 
            }
            
            // 过滤作品
            val candidates = result.items.filter { item ->
                // 最少图片数过滤
                if (item.pageCount < config.minPages) return@filter false
                
                // 去重
                if (seenIds.contains(item.id)) return@filter false
                seenIds.add(item.id)
                
                // AI 过滤
                when (config.aiMode) {
                    "none" -> !item.isAI // 排除AI，只保留非AI
                    "ai" -> item.isAI    // 仅AI，只保留AI作品
                    else -> true         // allow，不过滤
                }
                
                // R18 过滤
                when (config.r18Mode) {
                    "none" -> item.r18 == "none" || item.r18 == "r18" || item.r18 == "r18g"
                    "r18" -> item.r18 == "r18"
                    "r18g" -> item.r18 == "r18g"
                    else -> true // allow
                }
            }
            
            // 添加到结果
            _state.update { 
                it.copy(
                    candidateCount = it.candidateCount + candidates.size,
                    matchedCount = it.matchedCount + candidates.size,
                    results = it.results + candidates,
                    debugInfo = result.debugInfo
                )
            }
            
            // 移动到下一页
            _state.update { it.copy(currentPage = currentPage + 1) }
            
            // 页间延迟
            delay(1000)
        }
    }
}

/**
 * 收集器状态
 */
data class CollectorState(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val config: SearchConfig? = null,
    val currentPage: Int = 1,
    val pagesVisited: Int = 0,
    val candidateCount: Int = 0,
    val matchedCount: Int = 0,
    val results: List<Artwork> = emptyList(),
    val state: TaskState = TaskState.IDLE,
    val lastError: String? = null,
    val consecutiveErrors: Int = 0,
    val debugInfo: String = ""
) {
    // 计算属性：实际运行中
    val isActivelyRunning: Boolean get() = isRunning && !isPaused
}
