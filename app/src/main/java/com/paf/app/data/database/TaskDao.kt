package com.paf.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 任务 DAO
 */
@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>
    
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long
    
    @Update
    suspend fun updateTask(task: TaskEntity)
    
    @Delete
    suspend fun deleteTask(task: TaskEntity)
    
    @Query("DELETE FROM tasks")
    suspend fun clearAll()
    
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLastTask(): TaskEntity?
}

/**
 * 任务实体
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val keyword: String,
    val minPages: Int,
    val startPage: Int,
    val endPage: Int,
    val targetCount: Int,
    val batchSize: Int,
    val currentPage: Int = 1,
    val matchedCount: Int = 0,
    val state: String = "idle",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastError: String? = null,
    val resultJson: String = "" // JSON 序列化的结果
)

/**
 * 数据库
 */
@Database(entities = [TaskEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
}
