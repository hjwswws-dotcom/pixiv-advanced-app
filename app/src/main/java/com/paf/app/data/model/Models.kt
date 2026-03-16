package com.paf.app.data.model

import kotlinx.serialization.Serializable

/**
 * 搜索结果中的作品
 */
@Serializable
data class Artwork(
    val id: Long,
    val title: String,
    val author: String,
    val pageCount: Int,
    val thumbnail: String,
    val url: String,
    val tags: List<String> = emptyList(),
    val isAI: Boolean = false,
    val r18: String = "none" // none, r18, r18g
)

/**
 * 搜索参数配置
 */
@Serializable
data class SearchConfig(
    val keyword: String = "",
    val minPages: Int = 2,
    val startPage: Int = 1,
    val endPage: Int = -1, // -1 表示无限
    val targetCount: Int = 20,
    val batchSize: Int = 10,
    val aiMode: String = "allow", // allow(不过滤), none(排除AI), ai(仅AI)
    val r18Mode: String = "allow" // allow, none, r18, r18g
)

/**
 * 任务状态枚举
 */
enum class TaskState {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    ERROR,
    STOPPED
}
