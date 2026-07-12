package com.zhixing.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TimeGridLayout 纯布局数学的单元测试。
 *
 * 配置：显示范围 06:00-23:00（360-1380 分钟），粒度 30 分钟。
 * 行数 = (1380-360)/30 = 34 行。
 */
class TimeGridLayoutTest {

    private val grid = TimeGridLayout(
        startMinute = 360,   // 06:00
        endMinute = 1380,    // 23:00
        granularityMinutes = 30,
    )

    private val rowHeight = 48f

    @Test
    fun row_count_is_34() {
        assertThat(grid.rowCount).isEqualTo(34)
    }

    @Test
    fun yPositionFor_10_o_clock() {
        // 10:00 = 600min; (600-360)/30 = 8 行; 8 * 48 = 384
        assertThat(grid.yPositionFor(600, rowHeight)).isEqualTo(384f)
    }

    @Test
    fun yPositionFor_start_of_range_is_zero() {
        assertThat(grid.yPositionFor(360, rowHeight)).isEqualTo(0f)
    }

    @Test
    fun yPositionFor_before_start_clamps_to_zero() {
        // 05:00 早于显示范围，贴顶
        assertThat(grid.yPositionFor(300, rowHeight)).isEqualTo(0f)
    }

    @Test
    fun heightFor_one_hour_is_two_rows() {
        // 10:00-11:00 = 60min; 60/30 = 2 行; 2 * 48 = 96
        assertThat(grid.heightFor(600, 660, rowHeight)).isEqualTo(96f)
    }

    @Test
    fun heightFor_half_hour_is_one_row() {
        // 10:00-10:30 = 30min; 1 行; 48
        assertThat(grid.heightFor(600, 630, rowHeight)).isEqualTo(48f)
    }

    @Test
    fun heightFor_ten_min_clamps_to_minHeightRows() {
        // 10min < 粒度(30min)：应贴到 minHeightRows 地板（0.75 行 = 36f），而非占满整行 48f。
        assertThat(grid.heightFor(600, 610, rowHeight)).isEqualTo(36f)
    }

    @Test
    fun heightFor_twenty_eight_min_keeps_proportion_between_floor_and_one_row() {
        // 28min → 28/30 ≈ 0.933 行 ≈ 44.8f，体现"地板以上仍保留比例差异"，而非全被压成 36f。
        assertThat(grid.heightFor(600, 628, rowHeight)).isEqualTo(44.8f)
    }

    @Test
    fun heightFor_zero_duration_clamps_to_floor() {
        // 退化 0min 时长也应受 minHeightRows 兜底，与 10min 同高 36f。
        assertThat(grid.heightFor(600, 600, rowHeight)).isEqualTo(36f)
    }

    @Test
    fun yPositionFor_09_10_sits_fractionally_into_row() {
        // 09:10 = 550min；(550-360)/30 = 6.333… 行 = 304f（不再整除吸附到 6 行 = 288f）。
        assertThat(grid.yPositionFor(550, rowHeight)).isEqualTo(304f)
    }

    @Test
    fun labelForRow_shows_half_hour_ticks() {
        // 第 0 行 = 06:00
        assertThat(grid.labelForRow(0)).isEqualTo("06:00")
        // 第 1 行 = 06:30
        assertThat(grid.labelForRow(1)).isEqualTo("06:30")
        // 第 8 行 = 10:00
        assertThat(grid.labelForRow(8)).isEqualTo("10:00")
        // 最后行 = 22:30（第34行起是23:00, 标签取第33行即22:30）
        assertThat(grid.labelForRow(33)).isEqualTo("22:30")
    }

    @Test
    fun total_height_is_row_count_times_row_height() {
        // 34 * 48 = 1632
        assertThat(grid.totalHeight(rowHeight)).isEqualTo(1632f)
    }
}
