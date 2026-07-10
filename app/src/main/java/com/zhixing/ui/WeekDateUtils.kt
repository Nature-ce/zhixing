package com.zhixing.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 周视图日期计算工具。纯逻辑、零 Android UI 依赖。
 *
 * 以周一为一周起点（符合中文使用习惯），
 * 给定任意日期返回该周 7 天的日期列表。
 */
object WeekDateUtils {

    private val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * 给定日期所在周的 7 个日期字符串（周一 .. 周日），格式 yyyy-MM-dd。
     */
    fun weekDatesOfDay(dateStr: String): List<String> {
        val cal = Calendar.getInstance()
        cal.time = parser.parse(dateStr) ?: return emptyList()

        // 调整到本周一：Calendar.DAY_OF_WEEK 周日=1 … 周六=7，需映射回"距周一几天"
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val offsetToMonday = when (dayOfWeek) {
            Calendar.SUNDAY -> -6
            else -> Calendar.MONDAY - dayOfWeek
        }
        cal.add(Calendar.DAY_OF_MONTH, offsetToMonday)

        val result = mutableListOf<String>()
        repeat(7) {
            result.add(parser.format(cal.time))
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return result
    }

    /**
     * 根据日期推行星期几简短标签（周一 .. 周日）。
     */
    fun dayLabel(dateStr: String): String {
        val cal = Calendar.getInstance()
        cal.time = parser.parse(dateStr) ?: return ""
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> ""
        }
    }

    /**
     * 将 (yyyy-MM-dd, 当日分钟数) 转为可读时间 "HH:MM"。
     */
    fun formatHourMinute(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }
}
