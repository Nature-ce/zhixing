package com.zhixing.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD: 周视图日期计算纯逻辑。
 *
 * - weekDatesOfDay: 给定任意日期，返回它所在周的 7 天（周一 .. 周日）
 * - dayLabel: 根据日期推算星期几的简短标签
 */
class WeekDateUtilsTest {

    @Test
    fun week_of_wednesday_starts_on_monday() {
        // 2026-07-08 是周三
        val week = WeekDateUtils.weekDatesOfDay("2026-07-08")

        assertThat(week).hasSize(7)
        assertThat(week.first()).isEqualTo("2026-07-06") // 周一
        assertThat(week.last()).isEqualTo("2026-07-12")  // 周日
    }

    @Test
    fun week_of_monday_returns_itself_as_first() {
        val week = WeekDateUtils.weekDatesOfDay("2026-07-06")

        assertThat(week.first()).isEqualTo("2026-07-06")
        assertThat(week.last()).isEqualTo("2026-07-12")
    }

    @Test
    fun week_of_sunday_returns_itself_as_last() {
        val week = WeekDateUtils.weekDatesOfDay("2026-07-12")

        assertThat(week.first()).isEqualTo("2026-07-06")
        assertThat(week.last()).isEqualTo("2026-07-12")
    }

    @Test
    fun week_across_month_boundary() {
        // 2026-08-01 是周六，所在周跨 7 月 / 8 月
        val week = WeekDateUtils.weekDatesOfDay("2026-08-01")

        assertThat(week.first()).isEqualTo("2026-07-27") // 周一
        assertThat(week.last()).isEqualTo("2026-08-02")  // 周日
    }

    @Test
    fun day_label_shows_weekday_name() {
        // 仅验证"给定日期推算星期标签"不抛异常且返回预期值
        assertThat(WeekDateUtils.dayLabel("2026-07-06")).isEqualTo("周一") // 2026-07-06 周一
        assertThat(WeekDateUtils.dayLabel("2026-07-12")).isEqualTo("周日") // 2026-07-12 周日
    }
}
