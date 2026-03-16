package com.paf.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paf.app.data.model.Artwork
import com.paf.app.data.model.SearchConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "search_history")

/**
 * 搜索历史记录
 */
@Serializable
data class SearchHistory(
    val id: Long = System.currentTimeMillis(),
    val config: SearchConfig,
    val resultCount: Int,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 搜索结果（包含配置和作品列表）
 */
@Serializable
data class SearchResultData(
    val config: SearchConfig,
    val artworks: List<Artwork>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 搜索历史仓库 - 使用 DataStore 持久化
 */
class SearchHistoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val SEARCH_HISTORY_KEY = stringPreferencesKey("search_history")
        private val LATEST_RESULT_KEY = stringPreferencesKey("latest_result")
    }

    /**
     * 保存搜索结果（最新一次）
     */
    suspend fun saveLatestResult(config: SearchConfig, artworks: List<Artwork>) {
        val resultData = SearchResultData(config, artworks)
        val jsonString = json.encodeToString(resultData)
        
        context.dataStore.edit { preferences ->
            preferences[LATEST_RESULT_KEY] = jsonString
        }
    }

    /**
     * 读取最新搜索结果
     */
    fun getLatestResult(): Flow<SearchResultData?> {
        return context.dataStore.data.map { preferences ->
            preferences[LATEST_RESULT_KEY]?.let { jsonString ->
                try {
                    json.decodeFromString<SearchResultData>(jsonString)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /**
     * 保存搜索历史记录
     */
    suspend fun saveSearchHistory(config: SearchConfig, resultCount: Int) {
        val history = SearchHistory(config = config, resultCount = resultCount)
        
        context.dataStore.edit { preferences ->
            val existingHistory = preferences[SEARCH_HISTORY_KEY]?.let { jsonString ->
                try {
                    json.decodeFromString<List<SearchHistory>>(jsonString).toMutableList()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } ?: mutableListOf()

            // 添加新记录到开头
            existingHistory.add(0, history)

            // 只保留最近20条
            val trimmedHistory = existingHistory.take(20)

            preferences[SEARCH_HISTORY_KEY] = json.encodeToString(trimmedHistory)
        }
    }

    /**
     * 读取搜索历史记录
     */
    fun getSearchHistory(): Flow<List<SearchHistory>> {
        return context.dataStore.data.map { preferences ->
            preferences[SEARCH_HISTORY_KEY]?.let { jsonString ->
                try {
                    json.decodeFromString<List<SearchHistory>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } ?: emptyList()
        }
    }

    /**
     * 清除历史记录
     */
    suspend fun clearHistory() {
        context.dataStore.edit { preferences ->
            preferences.remove(SEARCH_HISTORY_KEY)
            preferences.remove(LATEST_RESULT_KEY)
        }
    }
}
