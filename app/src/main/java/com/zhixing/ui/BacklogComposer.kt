package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity

/**
 * Backlog 中的子项目条目。
 */
data class BacklogItem(
    val id: Long,
    val title: String,
    val estimatedDuration: Int? = null,
    // 所属任务的 id，供 BacklogGrouper 按键分组。
    val taskId: Long = 0L,
    // 所属任务的标题，供分组头显示。
    val taskTitle: String = "",
)

/**
 * 按父任务归组的 backlog：一组 = 一个任务下的 backlog 子项目。
 */
data class TaskBacklogGroup(
    val taskId: Long,
    val taskTitle: String,
    val items: List<BacklogItem>,
)

/**
 * Backlog 分组器。纯逻辑，零 Android 依赖。
 *
 * 把扁平的 backlog 条目按 taskId 归组，组顺序按条目首次出现的顺序；
 * 组头标签取自该组首条的任务标题（任务标题在组内一致）。
 */
object BacklogGrouper {

    fun group(items: List<BacklogItem>): List<TaskBacklogGroup> =
        items.groupBy { it.taskId }.map { (taskId, subs) ->
            TaskBacklogGroup(
                taskId = taskId,
                taskTitle = subs.first().taskTitle.ifBlank { "未知任务" },
                items = subs,
            )
        }
}

/**
 * Backlog 面板组装器。纯逻辑，零 Android 依赖。
 *
 * 筛选规则：
 *   - 只保留 backlog 状态的子项目
 *   - 排除当天已排期的子项目（已排期的出现在日视图，不应重复出现在 backlog）
 */
object BacklogComposer {

    fun assemble(
        subprojects: List<SubprojectEntity>,
        scheduledSubprojectIds: Set<Long>,
        // taskId → 任务标题；用于分组头显示来源任务。
        taskTitles: Map<Long, String>,
    ): List<BacklogItem> {
        return subprojects
            .filter { it.status == "backlog" }
            .filter { it.id !in scheduledSubprojectIds }
            .map {
                BacklogItem(
                    id = it.id,
                    title = it.title,
                    estimatedDuration = it.estimatedDuration,
                    taskId = it.taskId,
                    taskTitle = taskTitles[it.taskId] ?: "",
                )
            }
    }
}
