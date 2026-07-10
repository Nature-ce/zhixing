package com.zhixing.data

import com.zhixing.ui.ScheduleTimeGrid
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD: drop 落点 y 坐标 → 排期起始分钟。
 *
 * 纯领域逻辑：用户把子项目拖到格栅的某一位置，根据落点的 y 像素
 * + 行高反算出起始分钟，并吸附到粒度（30 分钟）。
 */
class DropScheduleCalculatorTest {

    private val grid = ScheduleTimeGrid
    private val rowHeight = 48f

    @Test
    fun `drop at top yields grid start minute`() {
        // y=0 对应格栅顶端 = 06:00 = 360
        assertThat(DropScheduleCalculator.minuteFromY(0f, rowHeight, grid)).isEqualTo(360)
    }

    @Test
    fun `drop at row 6 yields 09_00`() {
        // 09:00 = 540。第 6 行顶部 y = 6 * 48 = 288
        val y = 6 * rowHeight
        assertThat(DropScheduleCalculator.minuteFromY(y, rowHeight, grid)).isEqualTo(540)
    }

    @Test
    fun `drop in middle of row snaps to that row`() {
        // 第 6 行中段 y = 6*48 + 10 = 298 → 仍应吸附为 09:00 = 540（向下取整到本行顶）
        val y = 6 * rowHeight + 10f
        assertThat(DropScheduleCalculator.minuteFromY(y, rowHeight, grid)).isEqualTo(540)
    }

    @Test
    fun `negative y clamps to start minute`() {
        assertThat(DropScheduleCalculator.minuteFromY(-50f, rowHeight, grid)).isEqualTo(360)
    }

    @Test
    fun `y beyond bottom clamps to last startable minute`() {
        // 底端之外 → 贴到最后一起始分钟（endMinute - granularity = 1380 - 30 = 1350 = 22:30）
        val y = grid.rowCount * rowHeight + 100f
        assertThat(DropScheduleCalculator.minuteFromY(y, rowHeight, grid)).isEqualTo(1350)
    }

    @Test
    fun `slotFromDrop combines position and duration`() {
        // drop 在 09:00 位置（第 6 行顶），时长 60 分钟 → (540, 600)
        val y = 6 * rowHeight
        val slot = DropScheduleCalculator.slotFromDrop(y, rowHeight, grid, 60)
        assertThat(slot).isEqualTo(ScheduleSlot(540, 600))
    }

    @Test
    fun `slotFromDrop end clamps to grid end minute`() {
        // drop 在 22:30（最后一起始），时长 60 分钟 → end 贴到 1380，不是 1410
        val y = 33 * rowHeight
        val slot = DropScheduleCalculator.slotFromDrop(y, rowHeight, grid, 60)
        assertThat(slot).isEqualTo(ScheduleSlot(1350, 1380))
    }
}
