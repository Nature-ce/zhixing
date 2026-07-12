package com.zhixing.ui

/**
 * 时间格栅布局配置与计算。纯逻辑，零 Android 依赖。
 *
 * 把"一天中的分钟数"映射为"格栅中的 y 坐标 / 块高度"，
 * 供日视图和周视图的时间格栅渲染复用。
 *
 * @param startMinute 显示范围起始（从 0 点起的分钟数，如 06:00 = 360）
 * @param endMinute 显示范围结束（如 23:00 = 1380）
 * @param granularityMinutes 每行代表的分钟数（如 30 = 半小时间隔）
 */
data class TimeGridLayout(
    val startMinute: Int,
    val endMinute: Int,
    val granularityMinutes: Int,
) {
    val rowCount: Int = (endMinute - startMinute) / granularityMinutes

    /**
     * 块高的最小行数（容下单行标题 + 内边距，避免文字截断）。
     * 行高 48dp 时 = 36dp。
     */
    val minHeightRows: Float = 0.75f

    /**
     * 给定分钟数，返回在格栅中的 y 偏移（像素）。
     * 早于 startMinute 的贴顶（返回 0）。
     * 使用精确分钟比例定位（不再整除吸附），让短项目在带内按真实起始位置呈现。
     */
    fun yPositionFor(minute: Int, rowHeight: Float): Float {
        val clamped = minute.coerceAtLeast(startMinute)
        val rowsFromTop = (clamped - startMinute).toFloat() / granularityMinutes
        return rowsFromTop * rowHeight
    }

    /**
     * 给定开始/结束分钟，返回块高度（像素）。
     *
     * 高度按真实时长比例计算；不足 [minHeightRows] 的贴到地板，
     * 保证最短项目也能完整显示单行标题而不截断。
     */
    fun heightFor(startMinute: Int, endMinute: Int, rowHeight: Float): Float {
        val duration = (endMinute - startMinute).coerceAtLeast(0)
        val rows = (duration.toFloat() / granularityMinutes).coerceAtLeast(minHeightRows)
        return rows * rowHeight
    }

    /**
     * 第 row 行的时间标签（如 "06:00"、"06:30"）。
     */
    fun labelForRow(row: Int): String {
        val minute = startMinute + row * granularityMinutes
        val h = minute / 60
        val m = minute % 60
        return "%02d:%02d".format(h, m)
    }

    /**
     * 格栅总高度（像素）。
     */
    fun totalHeight(rowHeight: Float): Float = rowCount * rowHeight
}

/**
 * 默认日程格栅：06:00-23:00，30 分钟粒度。
 */
val ScheduleTimeGrid = TimeGridLayout(
    startMinute = 360,
    endMinute = 1380,
    granularityMinutes = 30,
)

/** 将从 0 点起的分钟数格式化为 "HH:MM"。 */
fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

/** 将起止分钟数格式化为 "HH:MM-HH:MM" 时间段。 */
fun formatTimeRange(startMinutes: Int, endMinutes: Int): String =
    "${formatMinutes(startMinutes)}-${formatMinutes(endMinutes)}"
