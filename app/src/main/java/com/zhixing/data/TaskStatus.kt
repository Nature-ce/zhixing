package com.zhixing.data

/**
 * 任务状态解析。纯领域逻辑，零 Android 依赖。
 *
 * 终态：已完成、已放弃。
 * 规则：所有子项目都进入终态时，任务自动变为"进行中" -> "已完成"；
 *       任一子项目仍在 backlog 或 已排期，任务保持"进行中"。
 */
object TaskStatus {
    private val TERMINAL = setOf("已完成", "已放弃")

    fun resolve(subprojectStatuses: List<String>): String {
        val allTerminal = subprojectStatuses.isNotEmpty() &&
            subprojectStatuses.all { it in TERMINAL }
        return if (allTerminal) "已完成" else "进行中"
    }
}
