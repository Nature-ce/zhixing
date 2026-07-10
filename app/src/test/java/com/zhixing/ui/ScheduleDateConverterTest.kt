package com.zhixing.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * 日期字符串与 UTC 毫秒互转的纯逻辑单元测试。
 *
 * 为 Material3 DatePicker（内部用 UTC 午夜毫秒）与"yyyy-MM-dd"字符串
 * 之间提供可测的纯函数桥梁。
 */
class ScheduleDateConverterTest {

    @Test
    fun millisToDateStrUtc_round_trip_for_mid_2026() {
        val millis = localMillisAtMidnight("2026-07-08")
        assertThat(millisToDateStrUtc(millis)).isEqualTo("2026-07-08")
    }

    @Test
    fun millisToDateStrUtc_round_trip_for_year_boundary() {
        val millis = localMillisAtMidnight("2025-01-01")
        assertThat(millisToDateStrUtc(millis)).isEqualTo("2025-01-01")
    }
}
