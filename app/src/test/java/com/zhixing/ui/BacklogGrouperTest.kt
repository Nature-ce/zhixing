package com.zhixing.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD (RED->GREEN): BacklogGrouper 按父任务把 backlog 子项目分组。
 *
 * 验证：
 *   - 来自不同任务的子项目落到不同组，组头 = 任务标题
 *   - 同任务的子项目归入同一组
 */
class BacklogGrouperTest {

    @Test
    fun groups_by_parent_task() {
        val items = listOf(
            BacklogItem(id = 1, title = "选书目", taskId = 10L, taskTitle = "读书笔记"),
            BacklogItem(id = 2, title = "划重点", taskId = 10L, taskTitle = "读书笔记"),
            BacklogItem(id = 3, title = "挑礼物", taskId = 20L, taskTitle = "筹备婚礼"),
        )

        val groups = BacklogGrouper.group(items)

        assertThat(groups).hasSize(2)
        // 组顺序按首次出现
        assertThat(groups[0].taskId).isEqualTo(10L)
        assertThat(groups[0].taskTitle).isEqualTo("读书笔记")
        assertThat(groups[0].items).extracting<Long> { it.id }.containsExactly(1L, 2L)
        assertThat(groups[1].taskId).isEqualTo(20L)
        assertThat(groups[1].taskTitle).isEqualTo("筹备婚礼")
        assertThat(groups[1].items).extracting<Long> { it.id }.containsExactly(3L)
    }

    @Test
    fun empty_input_returns_empty() {
        assertThat(BacklogGrouper.group(emptyList())).isEmpty()
    }

    @Test
    fun single_task_merges_into_one_group() {
        val items = listOf(
            BacklogItem(id = 1, title = "选书目", taskId = 10L, taskTitle = "读书笔记"),
            BacklogItem(id = 2, title = "划重点", taskId = 10L, taskTitle = "读书笔记"),
        )

        val groups = BacklogGrouper.group(items)

        assertThat(groups).hasSize(1)
        assertThat(groups[0].items).extracting<Long> { it.id }.containsExactly(1L, 2L)
    }
}
