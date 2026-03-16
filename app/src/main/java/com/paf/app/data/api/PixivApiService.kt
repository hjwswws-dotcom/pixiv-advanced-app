package com.paf.app.data.api

import android.util.Log
import com.paf.app.data.model.Artwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Pixiv API 服务
 * 基于真实抓包的接口文档实现
 */
class PixivApiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    companion object {
        private const val TAG = "PixivAPI"
        private const val BASE_URL = "https://www.pixiv.net"
        private const val API_BASE = "$BASE_URL/ajax"
        
        // 存储 Cookie
        var cookies: String = ""
            set(value) {
                Log.d(TAG, "Setting cookies: ${value.length} chars")
                field = value
            }
        
        // 搜索接口 - 优先使用 artworks 端点
        fun buildSearchUrl(keyword: String, page: Int): String {
            return "$API_BASE/search/artworks/${encodeKeyword(keyword)}?word=${encodeKeyword(keyword)}&p=$page&lang=zh"
        }
        
        // 作品详情接口（用于获取 AI/R18 标签）
        fun buildDetailUrl(artworkId: Long): String {
            return "$API_BASE/works/$artworkId?lang=zh"
        }
        
        private fun encodeKeyword(keyword: String): String {
            return java.net.URLEncoder.encode(keyword, "UTF-8")
        }
    }
    
    /**
     * 验证登录状态 - 通过获取用户信息 API
     */
    suspend fun verifyLogin(): LoginVerifyResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verifying login, cookie length: ${cookies.length}")
            
            // 调用用户信息 API
            val url = "$API_BASE/user/me?lang=zh"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("x-requested-with", "XMLHttpRequest")
                .header("Referer", BASE_URL)
                .apply {
                    if (cookies.isNotEmpty()) {
                        addHeader("Cookie", cookies)
                        Log.d(TAG, "Added cookie to request: ${cookies.substring(0, minOf(100, cookies.length))}...")
                    }
                }
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            
            Log.d(TAG, "Verify response code: ${response.code}, body length: ${body.length}")
            
            if (response.code == 200 && body.isNotEmpty()) {
                try {
                    val json = JSONObject(body)
                    if (!json.optBoolean("error", true)) {
                        val userId = json.optJSONObject("body")?.optString("userId", "")
                        val userName = json.optJSONObject("body")?.optString("name", "")
                        Log.d(TAG, "Login verified! User: $userName (ID: $userId)")
                        return@withContext LoginVerifyResult(
                            success = true,
                            userId = userId,
                            userName = userName
                        )
                    } else {
                        val errorMsg = json.optString("message", "Unknown error")
                        Log.w(TAG, "Login verify failed: $errorMsg")
                        return@withContext LoginVerifyResult(success = false, error = errorMsg)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error: ${e.message}")
                    return@withContext LoginVerifyResult(success = false, error = e.message)
                }
            }
            
            return@withContext LoginVerifyResult(success = false, error = "HTTP ${response.code}")
        } catch (e: Exception) {
            Log.e(TAG, "Verify error: ${e.message}")
            return@withContext LoginVerifyResult(success = false, error = e.message)
        }
    }
    
    /**
     * 搜索作品 - 带审计日志
     */
    suspend fun search(keyword: String, page: Int): SearchResult = withContext(Dispatchers.IO) {
        try {
            val url = buildSearchUrl(keyword, page)
            
            // 审计日志
            Log.d(TAG, "===== 搜索审计开始 =====")
            Log.d(TAG, "URL: $url")
            Log.d(TAG, "Cookie: ${if (cookies.isNotEmpty()) "yes" else "no"}")
            
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("x-requested-with", "XMLHttpRequest")
                .header("Referer", BASE_URL)
                .apply {
                    if (cookies.isNotEmpty()) {
                        addHeader("Cookie", cookies)
                    }
                }
                .build()
            
            val response = client.newCall(request).execute()
            
            Log.d(TAG, "HTTP状态: ${response.code}")
            
            if (!response.isSuccessful) {
                return@withContext SearchResult(
                    success = false,
                    error = "HTTP ${response.code}",
                    items = emptyList()
                )
            }
            
            val body = response.body?.string() ?: return@withContext SearchResult(
                success = false,
                error = "Empty response",
                items = emptyList()
            )
            
            // 检查响应类型
            val isHtml = body.trim().startsWith("<") || body.trim().startsWith("<!DOCTYPE")
            if (isHtml) {
                Log.w(TAG, "警告: 响应是HTML!")
                Log.d(TAG, "HTML内容: ${body.take(200)}")
            } else {
                // 打印JSON结构
                try {
                    val json = JSONObject(body)
                    val topKeys = json.keys().asSequence().toList()
                    Log.d(TAG, "顶层key: $topKeys")
                    val bodyObj = json.optJSONObject("body")
                    val bodyKeys = bodyObj?.keys()?.asSequence()?.toList() ?: emptyList()
                    Log.d(TAG, "body.key: $bodyKeys")
                    
                    // 检查解析路径
                    val hasIllust = bodyObj?.optJSONObject("illust") != null
                    val hasData = bodyObj?.optJSONArray("data") != null
                    Log.d(TAG, "body.illust存在: $hasIllust, body.data存在: $hasData")
                } catch (e: Exception) {
                    Log.e(TAG, "JSON解析错误: ${e.message}")
                }
            }
            
            val json = JSONObject(body)
            
            if (json.optBoolean("error", true)) {
                val errMsg = json.optString("message", "Unknown error")
                Log.e(TAG, "API错误: $errMsg")
                return@withContext SearchResult(
                    success = false,
                    error = errMsg,
                    items = emptyList()
                )
            }
            
            // 解析作品列表
            val items = parseSearchResults(json)
            
            Log.d(TAG, "解析结果数量: ${items.size}")
            Log.d(TAG, "===== 搜索审计结束 =====")
            
            // 构建调试信息字符串
            val debugInfo = buildString {
                appendLine("【证据1】URL: $url")
                appendLine("【证据1】HTTP: ${response.code} | Cookie: ${if (cookies.isNotEmpty()) "有" else "无"}")
                if (isHtml) {
                    appendLine("【证据2】⚠️ 返回HTML不是JSON")
                    appendLine("【证据2】HTML: ${body.take(150)}")
                } else {
                    appendLine("【证据2】JSON响应正常")
                }
                appendLine("【证据3】候选数: ${items.size}")
            }
            
            SearchResult(
                success = true,
                items = items,
                hasNext = items.isNotEmpty(),
                debugInfo = debugInfo
            )
            
        } catch (e: Exception) {
            SearchResult(
                success = false,
                error = e.message ?: "Unknown error",
                items = emptyList()
            )
        }
    }
    
    /**
     * 解析搜索结果
     * 字段来源：id, title, userName, url, pageCount, tags
     */
    private fun parseSearchResults(json: JSONObject): List<Artwork> {
        val items = mutableListOf<Artwork>()
        
        try {
            val body = json.getJSONObject("body")
            
            // 尝试获取 illust.data
            val illust = body.optJSONObject("illust")
            if (illust != null) {
                val data = illust.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        items.add(parseArtwork(item))
                    }
                }
            }
            
            // 如果 illust.data 为空，尝试 body.data
            if (items.isEmpty()) {
                val data = body.optJSONArray("data")
                if (data != null) {
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        items.add(parseArtwork(item))
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return items
    }
    
    /**
     * 解析单个作品
     */
    private fun parseArtwork(json: JSONObject): Artwork {
        val id = json.getLong("id")
        val title = json.optString("title", "")
        val author = json.optString("userName", "")
        
        // 处理缩略图 URL
        val url = json.optString("url", "")
        val thumbnail = processThumbnail(url)
        
        // pageCount 字段
        val pageCount = json.optInt("pageCount", 1)
        
        // 标签
        val tagsArray = json.optJSONArray("tags")
        val tags = mutableListOf<String>()
        if (tagsArray != null) {
            for (i in 0 until tagsArray.length()) {
                tags.add(tagsArray.getString(i))
            }
        }
        
        // 判断 AI 和 R18
        val isAI = tags.any { it == "AI生成" || it == "AI" }
        val r18 = when {
            tags.any { it == "R-18G" || it == "R18G" } -> "r18g"
            tags.any { it == "R-18" || it == "R18" } -> "r18"
            else -> "none"
        }
        
        return Artwork(
            id = id,
            title = title,
            author = author,
            pageCount = pageCount,
            thumbnail = thumbnail,
            url = "$BASE_URL/artworks/$id",
            tags = tags,
            isAI = isAI,
            r18 = r18
        )
    }
    
    /**
     * 处理缩略图 URL - 转换为 i.pixiv.re
     */
    private fun processThumbnail(url: String): String {
        if (url.isEmpty()) return ""
        return url
            .replace(Regex("https?://[^/]+"), "https://i.pixiv.re")
            .replace("/c/150x150/", "/c/400x400/")
            .replace("/c/120x120/", "/c/400x400/")
    }
    
    /**
     * 获取作品详情（用于获取更准确的 AI/R18 信息）
     */
    suspend fun getArtworkDetail(artworkId: Long): Artwork? = withContext(Dispatchers.IO) {
        try {
            val url = buildDetailUrl(artworkId)
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("x-requested-with", "XMLHttpRequest")
                .header("Referer", BASE_URL)
                .apply {
                    if (cookies.isNotEmpty()) {
                        addHeader("Cookie", cookies)
                    }
                }
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) return@withContext null
            
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            
            if (json.optBoolean("error", true)) return@withContext null
            
            val bodyObj = json.getJSONObject("body")
            
            // 获取标签
            val tagsArray = bodyObj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArray != null) {
                for (i in 0 until tagsArray.length()) {
                    val tagObj = tagsArray.getJSONObject(i)
                    tags.add(tagObj.optString("tag", ""))
                }
            }
            
            val isAI = tags.any { it == "AI生成" || it == "AI" }
            val r18 = when {
                tags.any { it == "R-18G" || it == "R18G" } -> "r18g"
                tags.any { it == "R-18" || it == "R18" } -> "r18"
                else -> "none"
            }
            
            Artwork(
                id = artworkId,
                title = bodyObj.optString("title", ""),
                author = bodyObj.optString("userName", ""),
                pageCount = bodyObj.optInt("pageCount", 1),
                thumbnail = processThumbnail(bodyObj.optString("url", "")),
                url = "$BASE_URL/artworks/$artworkId",
                tags = tags,
                isAI = isAI,
                r18 = r18
            )
            
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * 搜索结果
 */
data class SearchResult(
    val success: Boolean,
    val error: String? = null,
    val items: List<Artwork> = emptyList(),
    val hasNext: Boolean = false,
    // 调试信息（简单字符串形式）
    val debugInfo: String = ""
)

/**
 * 登录验证结果
 */
data class LoginVerifyResult(
    val success: Boolean,
    val userId: String? = null,
    val userName: String? = null,
    val error: String? = null
)
