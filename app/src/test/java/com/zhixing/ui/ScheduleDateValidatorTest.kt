package com.zhixing.ui

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * 排期日期校验的纯逻辑单元测试。
 *
 * 规则：排期日期不允许早于今天（禁止补录过去日期的排期）。
 */
class ScheduleDateValidatorTest {

    @Test
    fun today_is_valid() {
        assertThat(isValidScheduleDate("2026-07-08", "2026-07-08")).isTrue()
    }

    @Test
    fun future_is_valid() {
        assertThat(isValidScheduleDate("2026-07-09", "2026-07-08")).isTrue()
    }

    @Test
    fun past_is_invalid() {
        assertThat(isValidScheduleDate("2026-07-07", "2026-07-08")).isFalse()
    }
}
