package com.zhixing.data

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

/**
 * TDD Round 2 (RED->GREEN): 子项目状态流转。
 *
 * CONTEXT.md 规则:
 *   - backlog -> 已排期 -> 已完成
 *   - backlog -> 已完成（跳过排期）
 *   - backlog / 已排期 -> 已放弃
 *   - 已完成 -> 已排期（误操作撤销）
 *   - 终态互不可逆
 *
 * 纯领域逻辑，零 Android 依赖。
 */
class SubprojectStateTest {

    @Test
    fun `backlog transitions to scheduled`() {
        assertThat(SubprojectState.transition("backlog", "已排期")).isEqualTo("已排期")
    }

    @Test
    fun `scheduled transitions to completed`() {
        assertThat(SubprojectState.transition("已排期", "已完成")).isEqualTo("已完成")
    }

    @Test
    fun `backlog can skip scheduling to completed`() {
        assertThat(SubprojectState.transition("backlog", "已完成")).isEqualTo("已完成")
    }

    @Test
    fun `backlog transitions to abandoned`() {
        assertThat(SubprojectState.transition("backlog", "已放弃")).isEqualTo("已放弃")
    }

    @Test
    fun `scheduled transitions to abandoned`() {
        assertThat(SubprojectState.transition("已排期", "已放弃")).isEqualTo("已放弃")
    }

    @Test
    fun `completed can undo back to scheduled`() {
        assertThat(SubprojectState.transition("已完成", "已排期")).isEqualTo("已排期")
    }

    @Test
    fun `scheduled can revert back to backlog`() {
        // 误排期撤销：已排期的子项目从日程表回退到 backlog。
        assertThat(SubprojectState.transition("已排期", "backlog")).isEqualTo("backlog")
    }

    @Test
    fun `abandoned cannot transition to completed`() {
        assertThatThrownBy { SubprojectState.transition("已放弃", "已完成") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `completed cannot transition to abandoned`() {
        assertThatThrownBy { SubprojectState.transition("已完成", "已放弃") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
