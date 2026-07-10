package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD: 拖放排期默认时长策略。
 *
 * 用户把 backlog 子项目拖到时间格，未手动指时长时的默认值：
 *   - 有 estimatedDuration 且 > 0 → 用它
 *   - 未设置或非正 → fallback 到一格（30 分钟）
 *
 * 纯领域逻辑，零 Android 依赖。
 */
class ScheduleDurationPolicyTest {

    @Test
    fun `uses estimatedDuration when set`() {
        val sub = SubprojectEntity(id = 1, taskId = 100, title = "选书目", estimatedDuration = 60)
        assertThat(resolveScheduledDuration(sub)).isEqualTo(60)
    }

    @Test
    fun `falls back to one slot when estimatedDuration is null`() {
        val sub = SubprojectEntity(id = 1, taskId = 100, title = "选书目", estimatedDuration = null)
        assertThat(resolveScheduledDuration(sub)).isEqualTo(30)
    }

    @Test
    fun `falls back to one slot when estimatedDuration is zero`() {
        val sub = SubprojectEntity(id = 1, taskId = 100, title = "选书目", estimatedDuration = 0)
        assertThat(resolveScheduledDuration(sub)).isEqualTo(30)
    }

    @Test
    fun `falls back to one slot when estimatedDuration is negative`() {
        val sub = SubprojectEntity(id = 1, taskId = 100, title = "选书目", estimatedDuration = -10)
        assertThat(resolveScheduledDuration(sub)).isEqualTo(30)
    }
}
