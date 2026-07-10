package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * JVM 单测：回顾解锁条件（纯逻辑，零 Android 依赖）。
 *
 * 验收标准：任务进行过程中"写回顾"入口锁定；任务完成后解锁。
 * 解锁 = task.status 已放弃，或所有子项目都已终态。
 */
class ReviewComposerTest {

    private fun task(status: String) = TaskEntity(id = 1, title = "读书笔记", status = status, createdAt = 1_000L)

    private fun sub(id: Long, status: String) =
        SubprojectEntity(id = id, taskId = 1, title = "子项目$id", status = status, createdAt = id * 1_000L)

    @Test
    fun in_progress_with_backlog_subproject_is_locked() {
        val unlocked = ReviewComposer.isReviewUnlocked(
            task("进行中"),
            listOf(sub(1, "backlog")),
        )
        assertThat(unlocked).isFalse()
    }

    @Test
    fun abandoned_task_is_unlocked() {
        val unlocked = ReviewComposer.isReviewUnlocked(
            task("已放弃"),
            emptyList(),
        )
        assertThat(unlocked).isTrue()
    }

    @Test
    fun completed_task_status_is_unlocked() {
        val unlocked = ReviewComposer.isReviewUnlocked(
            task("已完成"),
            listOf(sub(1, "已完成")),
        )
        assertThat(unlocked).isTrue()
    }

    @Test
    fun all_subprojects_terminal_unlocks_even_if_task_status_in_progress() {
        // task.status 未刷新为已完成（未触发自动完成），但子项目全终态 → 应解锁
        val unlocked = ReviewComposer.isReviewUnlocked(
            task("进行中"),
            listOf(sub(1, "已完成"), sub(2, "已放弃")),
        )
        assertThat(unlocked).isTrue()
    }

    @Test
    fun mixed_terminal_and_non_terminal_is_locked() {
        val unlocked = ReviewComposer.isReviewUnlocked(
            task("进行中"),
            listOf(sub(1, "已完成"), sub(2, "backlog")),
        )
        assertThat(unlocked).isFalse()
    }

    @Test
    fun no_subprojects_is_locked() {
        // 没有子项目的任务，无法判定完成 → 锁定
        val unlocked = ReviewComposer.isReviewUnlocked(
            task("进行中"),
            emptyList(),
        )
        assertThat(unlocked).isFalse()
    }
}
