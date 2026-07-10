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

        val result = BacklogComposer.assemble(subprojects, scheduledSubprojectIds)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1)
        assertThat(result[0].title).isEqualTo("选书目")
    }

    @Test
    fun excludes_subprojects_scheduled_today() {
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 1, title = "选书目", status = "backlog"),
            SubprojectEntity(id = 2, taskId = 1, title = "划重点", status = "backlog"),
        )
        // 子项目 2 今天已排期
        val scheduledSubprojectIds = setOf(2L)

        val result = BacklogComposer.assemble(subprojects, scheduledSubprojectIds)

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(1)
    }
}
