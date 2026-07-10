package com.zhixing.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TimelineComposer 的纯逻辑单元测试。
 *
 * 验证：
 *   - 终态子项目按 completedAt 升序排列
 *   - 未完成子项目排在后面，completedDate 为 null
 *   - 整体进度 completedCount / totalCount
 *   - icon 按状态区分（已完成=CHECK / 已放弃=ABANDON / 未完成=PENDING）
 *   - 空列表兜底为零进度 + 空 items
 */
class TimelineComposerTest {

    @Test
    fun orders_completed_items_by_completedAt_ascending() {
        val subprojects = listOf(
            sub(id = 1, title = "B", status = "已完成", completedAt = 2_000L),
            sub(id = 2, title = "A", status = "已完成", completedAt = 1_000L),
        )

        val result = TimelineComposer.assemble(subprojects)

        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].title).isEqualTo("A")
        assertThat(result.items[1].title).isEqualTo("B")
    }

    @Test
    fun places_non_terminal_items_after_completed_ones() {
        val subprojects = listOf(
            sub(id = 1, title = "未完成", status = "已排期", completedAt = null),
            sub(id = 2, title = "已完成", status = "已完成", completedAt = 1_000L),
        )

        val result = TimelineComposer.assemble(subprojects)

        assertThat(result.items[0].title).isEqualTo("已完成")
        assertThat(result.items[1].title).isEqualTo("未完成")
        assertThat(result.items[1].completedDate).isNull()
    }

    @Test
    fun computes_overall_progress() {
        val subprojects = listOf(
            sub(id = 1, title = "A", status = "已完成", completedAt = 1_000L),
            sub(id = 2, title = "B", status = "已放弃", completedAt = 2_000L),
            sub(id = 3, title = "C", status = "已排期", completedAt = null),
        )

        val result = TimelineComposer.assemble(subprojects)

        assertThat(result.completedCount).isEqualTo(2)
        assertThat(result.totalCount).isEqualTo(3)
    }

    @Test
    fun assigns_icon_by_status() {
        val subprojects = listOf(
            sub(id = 1, title = "A", status = "已完成", completedAt = 1_000L),
            sub(id = 2, title = "B", status = "已放弃", completedAt = 2_000L),
            sub(id = 3, title = "C", status = "已排期", completedAt = null),
        )

        val result = TimelineComposer.assemble(subprojects)

        // 按排序后顺序：A(CHECK), B(ABANDON), C(PENDING)
        assertThat(result.items.map { it.icon }).containsExactly(
            TimelineIcon.CHECK,
            TimelineIcon.ABANDON,
            TimelineIcon.PENDING,
        )
    }

    @Test
    fun empty_list_yields_zero_progress() {
        val result = TimelineComposer.assemble(emptyList())

        assertThat(result.completedCount).isEqualTo(0)
        assertThat(result.totalCount).isEqualTo(0)
        assertThat(result.items).isEmpty()
    }

    @Test
    fun formatCompletedDate_returns_yyyy_mm_dd_hh_mm() {
        // 1970-01-02 00:00:00 UTC = 86400 * 1000 ms
        assertThat(formatCompletedDate(86_400_000L)).isEqualTo("1970-01-02 00:00")
    }

    @Test
    fun formatCompletedDate_includes_nonzero_hour_and_minute() {
        // 1970-01-02 03:25:00 UTC = (86400 + 12300) * 1000 ms
        assertThat(formatCompletedDate(98_745_000L)).isEqualTo("1970-01-02 03:25")
    }

    private fun sub(
        id: Long,
        title: String,
        status: String,
        completedAt: Long?,
    ) = com.zhixing.data.entity.SubprojectEntity(
        id = id,
        taskId = 10L,
        title = title,
        status = status,
        createdAt = id * 1_000L,
        completedAt = completedAt,
    )
}
