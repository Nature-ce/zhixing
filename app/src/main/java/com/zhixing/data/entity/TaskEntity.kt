package com.zhixing.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 任务实体。字段定义来自 CONTEXT.md glossary。
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val status: String = "进行中",
    val targetCompletionDate: String? = null,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
    val reviewText: String = "",
)
