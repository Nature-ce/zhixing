package com.zhixing.ui

import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD (RED->GREEN): BacklogComposer 纯逻辑。
 *
 * 验证：
 *   - 只返回 backlog 状态的子项目
 *   - 排除当天已排期的子项目（已排期 → 不在 backlog 里）
 */
class BacklogComposerTest {

    @Test
    fun returns_only_backlog_subprojects() {
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 1, title = "选书目", status = "backlog"),
            SubprojectEntity(id = 2, taskId = 1, title = "划重点", status = "已完成"),
            SubprojectEntity(id = 3, taskId = 1, title = "写笔记", status = "已排期"),
        )
        val scheduledSubprojectIds = emptySet<Long>()

        val result = BacklogComposer.assemble(subprojects, scheduledSubprojectIds, emptyMap())

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1)
        assertThat(result[0].title).isEqualTo("选书目")
    }

    /**
     * 行为 #1：assemble 按子项目的 taskId 从 taskTitles 映射中取任务标题，
     * 填入 BacklogItem.taskTitle —— 让 backlog 区能区分子项目来自哪个任务。
     *
     * 当前 assemble 尚无 taskTitles 参数 → 编译失败（RED）。
     */
    @Test
    fun fillsTaskTitle_from_taskTitles_map() {
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 10, title = "选书目", status = "backlog"),
            SubprojectEntity(id = 2, taskId = 20, title = "挑礼物", status = "backlog"),
        )
        val scheduledSubprojectIds = emptySet<Long>()
        val taskTitles = mapOf(10L.to("读书笔记"), 20L.to("筹备婚礼"))

        val result = BacklogComposer.assemble(subprojects, scheduledSubprojectIds, taskTitles)

        assertThat(result).hasSize(2)
        assertThat(result[0].taskTitle).isEqualTo("读书笔记")
        assertThat(result[1].taskTitle).isEqualTo("筹备婚礼")
    }

    @Test
    fun excludes_subprojects_scheduled_today() {
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 1, title = "选书目", status = "backlog"),
            SubprojectEntity(id = 2, taskId = 1, title = "划重点", status = "backlog"),
        )
        // 子项目 2 今天已排期
        val scheduledSubprojectIds = setOf(2L)

        val result = BacklogComposer.assemble(subprojects, scheduledSubprojectIds, emptyMap())

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1)
    }
}
