package com.zhixing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 子项目实体。字段定义来自 CONTEXT.md glossary。
 *
 * 与 TaskEntity 是多对一关系：一个任务有多个子项目。
 * onDelete = CASCADE：任务被删除时，其下所有子项目自动删除。
 */
@Entity(
    tableName = "subprojects",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("taskId")],
)
data class SubprojectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val status: String = "backlog",
    val estimatedDuration: Int? = null,
    val createdAt: Long = 0L,
    val completedAt: Long? = null,
)
