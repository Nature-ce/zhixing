package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD: 任务列表项的 UI 模型组装。
 *
 * CONTEXT.md: 任务列表卡片——标题 + 状态标签 + 进度数字（如"3/8"）。
 *   - 所有子项目进入终态 → 状态 = 已完成
 *   - 任一子项目在 backlog/已排期 → 状态 = 进行中
 *   - 进度数字 = 已完成子项目数 / 子项目总数
 *
 * 纯领域组装逻辑，零 Android 依赖，在 JVM 上直接跑。
 */
class TaskListComposerTest {

    @Test
    fun `assemble returns completed status when all subprojects terminal`() {
        val task = TaskEntity(id = 1, title = "读书笔记", status = "进行中")
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 1, title = "选书目", status = "已完成"),
            SubprojectEntity(id = 2, taskId = 1, title = "划重点", status = "已放弃"),
        )

        val item = TaskListComposer.assemble(task, subprojects)

        assertThat(item.id).isEqualTo(1)
        assertThat(item.title).isEqualTo("读书笔记")
        assertThat(item.statusLabel).isEqualTo("已完成")
        assertThat(item.completedCount).isEqualTo(2)
        assertThat(item.totalCount).isEqualTo(2)
    }

    @Test
    fun `assemble returns abandoned status when task status is abandoned`() {
        val task = TaskEntity(id = 1, title = "读书笔记", status = "已放弃")
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 1, title = "选书目", status = "已放弃"),
            SubprojectEntity(id = 2, taskId = 1, title = "划重点", status = "已放弃"),
        )

        val item = TaskListComposer.assemble(task, subprojects)

        assertThat(item.statusLabel).isEqualTo("已放弃")
    }

    @Test
    fun `assemble returns in-progress status when subproject in backlog`() {
        val task = TaskEntity(id = 1, title = "写作", status = "进行中")
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 1, title = "大纲", status = "已完成"),
            SubprojectEntity(id = 2, taskId = 1, title = "第一段", status = "backlog"),
        )

        val item = TaskListComposer.assemble(task, subprojects)

        assertThat(item.statusLabel).isEqualTo("进行中")
        assertThat(item.completedCount).isEqualTo(1)
        assertThat(item.totalCount).isEqualTo(2)
    }

    @Test
    fun `assemble handles empty subprojects`() {
        val task = TaskEntity(id = 1, title = "新建任务", status = "进行中")

        val item = TaskListComposer.assemble(task, emptyList())

        assertThat(item.statusLabel).isEqualTo("进行中")
        assertThat(item.completedCount).isEqualTo(0)
        assertThat(item.totalCount).isEqualTo(0)
    }

    @Test
    fun `assemble fills subproject entries with terminal subprojects`() {
        val task = TaskEntity(id = 1, title = "读书笔记", status = "进行中")
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 1, title = "选书目", status = "已完成"),
            SubprojectEntity(id = 2, taskId = 1, title = "划重点", status = "已放弃"),
            SubprojectEntity(id = 3, taskId = 1, title = "写总结", status = "backlog"),
        )

        val item = TaskListComposer.assemble(task, subprojects)

        // 只列出终态子项目条目，供回顾卡片下挂展示
        assertThat(item.subprojectEntries.map { it.title }).containsExactly("选书目", "划重点")
        assertThat(item.subprojectEntries.map { it.id }).containsExactly(1L, 2L)
    }
}
