package com.zhixing.data

import com.zhixing.ui.TimeGridLayout

/**
 * 拖放落点 → 排期时间。纯领域逻辑，零 Android 依赖。
 *
 * 把 drop 的像素 y 坐标反算为"从 0 点起的分钟数"，并吸附到格栅粒度。
 */
object DropScheduleCalculator {

    /**
     * 由落点 y 像素（相对格栅顶端）反算起始分钟。
     *
     * 负数或顶端以上贴到 startMinute；超出底端贴到 endMinute 前一格。
     */
    fun minuteFromY(yPx: Float, rowHeight: Float, grid: TimeGridLayout): Int {
        if (yPx <= 0f) return grid.startMinute
        val rowsFromTop = (yPx / rowHeight).toInt()
        val rawMinute = grid.startMinute + rowsFromTop * grid.granularityMinutes
        val maxStart = grid.endMinute - grid.granularityMinutes
        return rawMinute.coerceIn(grid.startMinute, maxStart)
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
