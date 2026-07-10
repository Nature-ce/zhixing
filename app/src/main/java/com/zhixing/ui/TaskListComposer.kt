package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity

/**
 * 任务列表项的 UI 模型。纯展示层数据结构，不含 Room 注解。
 *
 * 进度数字 = completedCount / totalCount
 */
data class TaskListItem(
    val id: Long,
    val title: String,
    val statusLabel: String,
    val completedCount: Int,
    val totalCount: Int,
    /** 终态子项目条目（已完成 / 已放弃），供回顾卡片标题下方展示。 */
    val subprojectEntries: List<SubprojectEntry> = emptyList(),
)

/** 回顾列表里、任务卡片下的单个子项目条目（仅标题 + 状态，无 Room 注解）。 */
data class SubprojectEntry(
    val id: Long,
    val title: String,
    val status: String,
)

/**
 * 把数据库实体转成 UI 展示模型（纯函数，零 Android 依赖）。
 *
 * 状态判定沿用 TaskStatus.resolve 规则：所有子项目终态 → 已完成，否则进行中。
 * 进度数字：已完成子项目数（已完成或已放弃）/ 子项目总数。
 */
object TaskListComposer {

    private val TERMINAL = setOf("已完成", "已放弃")

    fun assemble(task: TaskEntity, subprojects: List<SubprojectEntity>): TaskListItem {
        val total = subprojects.size
        val completed = subprojects.count { it.status in TERMINAL }
        // 任务 status 已是终态时直接信任（区分"已完成"和"已放弃"）；
        // 否则从子项目推导：全终态 → 已完成，否则 → 进行中
        val statusLabel = when {
            task.status in TERMINAL -> task.status
            total > 0 && completed == total -> "已完成"
            else -> "进行中"
        }

        val entries = subprojects
            .filter { it.status in TERMINAL }
            .map { SubprojectEntry(id = it.id, title = it.title, status = it.status) }

        return TaskListItem(
            id = task.id,
            title = task.title,
            statusLabel = statusLabel,
            completedCount = completed,
            totalCount = total,
            subprojectEntries = entries,
        )
    }
}
