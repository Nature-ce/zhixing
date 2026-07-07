package com.zhixing.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD Round 1 (RED->GREEN): 任务状态随子项目终态自动变更。
 *
 * CONTEXT.md 规则:
 *   - 一个任务进入"已完成"，当且仅当它的所有子项目都进入终态（已完成 或 已放弃）。
 *   - 只要还有子项目停留在 backlog 或 已排期，任务就仍为"进行中"。
 *
 * 这是纯领域逻辑，零 Android 依赖，在 JVM 上直接跑。
 */
class TaskLifecycleTest {

    @Test
    fun `task auto-completes when all subprojects are terminal`() {
        assertThat(
            TaskStatus.resolve(
                listOf("已完成", "已放弃", "已完成", "已放弃")
            )
        ).isEqualTo("已完成")
    }

    @Test
    fun `task stays in-progress when a subproject is still in backlog`() {
        assertThat(
            TaskStatus.resolve(
                listOf("已完成", "backlog", "已完成")
            )
        ).isEqualTo("进行中")
    }

    @Test
    fun `task stays in-progress when a subproject is scheduled`() {
        assertThat(
            TaskStatus.resolve(
                listOf("已完成", "已排期")
            )
        ).isEqualTo("进行中")
    }
}
