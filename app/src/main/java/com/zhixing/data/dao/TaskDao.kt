package com.zhixing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhixing.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: Long, status: String)

    @Query("UPDATE tasks SET reviewText = :reviewText WHERE id = :id")
    suspend fun updateTaskReview(id: Long, reviewText: String)

    @Query("UPDATE tasks SET title = :title, description = :description WHERE id = :id")
    suspend fun updateTaskInfo(id: Long, title: String, description: String?)
}
