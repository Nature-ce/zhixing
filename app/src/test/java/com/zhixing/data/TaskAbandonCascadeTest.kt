package com.zhixing.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD: 任务放弃触发子项目全放弃（业务级 cascade）。
 *
 * CONTEXT.md 规则:
 *   - 任务被标记为"已放弃" → 其下所有子项目自动变为"已放弃"
 *   - 无论子项目原来在什么状态（backlog / 已排期 / 已完成），一律变终态
 *
 * 纯领域逻辑，零 Android 依赖。
 */
class TaskAbandonCascadeTest {

    @Test
    fun `abandon task cascades all subprojects to abandoned`() {
        val subprojectStatuses = listOf("backlog", "已排期", "已完成", "已放弃")

        val result = TaskLifecycle.abandonSubprojects(subprojectStatuses)

        assertThat(result).containsExactly("已放弃", "已放弃", "已放弃", "已放弃")
    }

    @Test
    fun `abandon task with empty subprojects returns empty`() {
        assertThat(TaskLifecycle.abandonSubprojects(emptyList())).isEmpty()
    }
}
