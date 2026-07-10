package com.zhixing.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * instrumented 测试：ScheduleTimePickerDialog（已废弃包装函数）。
 *
 * 该函数现委托给 [ScheduleDateTimePickerDialog]（Material3 滚轮 TimePicker），
 * "确认⇒回调分钟数"的行为已由 [ScheduleDateTimePickerTest] 完整覆盖（含日期）。
 * 此处仅保留"取消不触发确认"的端到端验证。
 */
class ScheduleTimePickerDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancel_does_not_invoke_onConfirm() {
        var confirmed = false

        composeRule.setContent {
            ZhixingTheme {
                ScheduleTimePickerDialog(
                    onConfirm = { _, _ -> confirmed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("取消").performClick()

        assert(!confirmed) { "onConfirm should not be invoked on cancel" }
    }
}
