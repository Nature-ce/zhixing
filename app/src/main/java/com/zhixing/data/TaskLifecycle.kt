package com.zhixing.data

/**
 * 任务生命周期行为。纯领域逻辑，零 Android 依赖。
 *
 * 任务被放弃时，其下所有子项目一律强制变为终态"已放弃"，
 * 无论子项目原来处于什么状态（backlog / 已排期 / 已完成）。
 */
object TaskLifecycle {

    fun abandonSubprojects(subprojectStatuses: List<String>): List<String> {
        return subprojectStatuses.map { "已放弃" }
    }
}
