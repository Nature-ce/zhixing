package com.zhixing.data

import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
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
    fun `drop at 09_05 within row snaps to 09_05`() {
        // 第 6 行（09:00）内、对应 09:05 的位置：y = 6*48 + (5/30)*48 = 288 + 8 = 296
        // 吸附到最近 5 分钟 → 545 = 09:05（不再是 09:00）
        val y = 6 * rowHeight + (5f / grid.granularityMinutes) * rowHeight
        assertThat(DropScheduleCalculator.minuteFromY(y, rowHeight, grid)).isEqualTo(545)
    }

    /**
     * 用户场景：20 分钟块已排在 7:30-7:50，拖 40 分钟块到 7:50 处。
     * 7:50 = 470 分钟，对应 y = (470-360)/30 * 48 = 176px。
     * 应吸附到 470（7:50），而非旧实现的 450（7:30，30 分钟取整）。
     */
    @Test
    fun `drop at 07_50 snaps to 07_50 not 07_30`() {
        // 07:50 = 470 分钟 → y = (470 - 360) / 30 * 48 = 176px
        val y = ((470 - 360).toFloat() / grid.granularityMinutes) * rowHeight
        assertThat(DropScheduleCalculator.minuteFromY(y, rowHeight, grid)).isEqualTo(470)
    }

    /**
     * 集成证明（用户完整场景）：
     * 已有 20 分钟块 7:30-7:50（同任务），把 40 分钟块拖到 7:50。
     *
     * 拖拽路径：slotFromDrop 在 7:50 落点吸附得 470-510；
     * 冲突检测：与已有 [450,470) 半开区间相接但不重叠 → 不冲突，拖放应成功。
     *
     * 旧实现（30 分钟吸附）会落到 450-490 → 与 [450,470) 重叠 → 假冲突拒绝。
     */
    @Test
    fun `drag 40min block to 07_50 adjacent to existing 20min block does not conflict`() {
        val yAt07_50 = ((470 - 360).toFloat() / grid.granularityMinutes) * rowHeight
        val slot = DropScheduleCalculator.slotFromDrop(yAt07_50, rowHeight, grid, 40)
        assertThat(slot).isEqualTo(ScheduleSlot(470, 510))

        // 已有同任务的 20 分钟块 7:30-7:50
        val existing = listOf(
            ScheduleEntity(id = 1, subprojectId = 1, date = "2026-07-09", startTime = 450, endTime = 470, createdAt = 1),
        )
        val subprojects = listOf(
            SubprojectEntity(id = 1, taskId = 100, title = "已有块"),
            SubprojectEntity(id = 2, taskId = 100, title = "新块"),
        )
        val conflict = ScheduleConflictDetector.hasConflict(
            subprojectId = 2, date = "2026-07-09",
            startTime = slot.start, endTime = slot.end,
            existingSchedules = existing, subprojects = subprojects,
        )
        // 半开区间 [450,470) 与 [470,510) 相接不重叠 → 不冲突
        assertThat(conflict).isFalse()
    }

    @Test
    fun `drop between 5 minute marks rounds to nearest`() {
        // 09:06 (546) → 靠近 09:05，四舍五入 → 545；09:09 (549) → 靠近 09:10 → 550
        val y6 = ((546 - 360).toFloat() / grid.granularityMinutes) * rowHeight
        val y9 = ((549 - 360).toFloat() / grid.granularityMinutes) * rowHeight
        assertThat(DropScheduleCalculator.minuteFromY(y6, rowHeight, grid)).isEqualTo(545)
        assertThat(DropScheduleCalculator.minuteFromY(y9, rowHeight, grid)).isEqualTo(550)
    }

    @Test
    fun `negative y clamps to start minute`() {
        assertThat(DropScheduleCalculator.minuteFromY(-50f, rowHeight, grid)).isEqualTo(360)
    }

    @Test
    fun `y beyond bottom clamps to last startable minute`() {
        // 底端之外 → 贴到最后一起始分钟（endMinute - 5 = 1380 - 5 = 1375 = 22:55）
        val y = grid.rowCount * rowHeight + 100f
        assertThat(DropScheduleCalculator.minuteFromY(y, rowHeight, grid)).isEqualTo(1375)
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
