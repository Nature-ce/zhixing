package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity

/**
 * Backlog 中的子项目条目。
 */
data class BacklogItem(
    val id: Long,
    val title: String,
    val estimatedDuration: Int? = null,
)

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
    ): List<BacklogItem> {
        return subprojects
            .filter { it.status == "backlog" }
            .filter { it.id !in scheduledSubprojectIds }
            .map { BacklogItem(id = it.id, title = it.title, estimatedDuration = it.estimatedDuration) }
    }
}
