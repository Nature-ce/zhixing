package com.zhixing.data

import com.zhixing.ui.TimeGridLayout

/**
 * 拖放落点 → 排期时间。纯领域逻辑，零 Android 依赖。
 *
 * 把 drop 的像素 y 坐标反算为连续分钟，再吸附到 [SNAP_GRANULARITY_MINUTES]（5 分钟）。
 * 吸附粒度独立于视觉格栅粒度（30 分钟）：格栅标签保持 :00/:30 干净，
 * 但块可以落在 7:50 等位置，与手动排期精度对齐。
 */
object DropScheduleCalculator {

    /** 拖拽落点吸附粒度（分钟）。独立于视觉格栅粒度。 */
    private const val SNAP_GRANULARITY_MINUTES = 5

    /**
     * 由落点 y 像素（相对格栅顶端）反算起始分钟。
     *
     * 先按行高比例算出连续分钟，再四舍五入到最近 5 分钟倍数（半值向上）。
     * 负数或顶端以上贴到 startMinute；超出底端贴到 endMinute 前一个可吸附位置。
     */
    fun minuteFromY(yPx: Float, rowHeight: Float, grid: TimeGridLayout): Int {
        if (yPx <= 0f) return grid.startMinute
        val exactMinute = grid.startMinute + (yPx / rowHeight) * grid.granularityMinutes
        val snapped = (Math.round(exactMinute / SNAP_GRANULARITY_MINUTES) * SNAP_GRANULARITY_MINUTES).toInt()
        val maxStart = grid.endMinute - SNAP_GRANULARITY_MINUTES
        return snapped.coerceIn(grid.startMinute, maxStart)
    }

    /**
     * 由落点 y 像素 + 排期时长，算出完整的起止分钟。
     *
     * @param durationMinutes 已解析好的时长（由调用方用 resolveScheduledDuration 算好）
     */
    fun slotFromDrop(yPx: Float, rowHeight: Float, grid: TimeGridLayout, durationMinutes: Int): ScheduleSlot {
        val start = minuteFromY(yPx, rowHeight, grid)
        val end = (start + durationMinutes).coerceAtMost(grid.endMinute)
        return ScheduleSlot(start, end)
    }
}

/** 排期时间段（从 0 点起的分钟数，半开区间 [start, end)）。 */
data class ScheduleSlot(val start: Int, val end: Int)
