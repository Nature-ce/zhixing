package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zhixing.ui.theme.ZhixingTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * instrumented 测试：排期日期+时间选择对话框。
 *
 * 验证新增的日期选择行为：
 *   - 过去日期 → 确认按钮 disabled + 显示"不能排到过去"
 *   - 今天/未来 → 确认 enabled
 *   - 确认回调返回 (date, startTime, endTime)
 */
class ScheduleDateTimePickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun past_date_disables_confirm_and_shows_error() {
        composeRule.setContent {
            ZhixingTheme {
                ScheduleDateTimePickerDialog(
                    initialDate = "2026-07-07",
                    today = "2026-07-08",
                    onConfirm = { _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        // 过去日期 → 确认按钮禁用
        composeRule.onNodeWithTag("ConfirmScheduleTime").assertIsNotEnabled()
        // 显示拦截提示
        composeRule.onNodeWithText("不能排到过去").assertIsDisplayed()
    }

    @Test
    fun today_date_keeps_confirm_enabled() {
        composeRule.setContent {
            ZhixingTheme {
                ScheduleDateTimePickerDialog(
                    initialDate = "2026-07-08",
                    today = "2026-07-08",
                    onConfirm = { _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        // 今天 + 默认合法时间段 → 确认 enabled
        composeRule.onNodeWithTag("ConfirmScheduleTime").assertIsEnabled()
    }

    @Test
    fun confirm_emits_selected_date_and_times() {
        var captured: Triple<String, Int, Int>? = null

        composeRule.setContent {
            ZhixingTheme {
                ScheduleDateTimePickerDialog(
                    initialDate = "2026-07-09",
                    today = "2026-07-08",
                    suggestedDurationMinutes = 60,
                    onConfirm = { date, start, end -> captured = Triple(date, start, end) },
                    onDismiss = {},
                )
            }
        }

        // 不拨动任何滚轮，直接确认 → 2026-07-09 + 09:00=540, 10:00=600
        composeRule.onNodeWithTag("ConfirmScheduleTime").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { captured != null }
        assertThat(captured).isEqualTo(Triple("2026-07-09", 540, 600))
    }

    @Test
    fun end_time_preview_reflects_start_plus_suggested_duration() {
        // 结束时间 = 开始时间 + 建议时长，并作为预览回显，用户无需手动输入结束时间。
        composeRule.setContent {
            ZhixingTheme {
                ScheduleDateTimePickerDialog(
                    initialDate = "2026-07-09",
                    today = "2026-07-08",
                    suggestedDurationMinutes = 60,
                    onConfirm = { _, _, _ -> },
                    onDismiss = {},
                )
            }
        }

        // 结束时间预览 = 开始 09:00 + 建议 60min = 10:00，回显给用户确认
        composeRule.onNodeWithTag("ScheduleEndTimePreview").assertTextEquals("10:00")
        // 确认按钮 enabled（结束晚于开始，恒成立）
        composeRule.onNodeWithTag("ConfirmScheduleTime").assertIsEnabled()
    }
}
