package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): 创建任务对话框。
 *
 * 验证：
 *   - 对话框显示标题输入框 + 确认按钮
 *   - 输入标题后点确认 → 回调 onConfirm(title)
 *   - 点取消 → 回调 onDismiss
 */
class CreateTaskDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialog_shows_title_input_and_buttons() {
        composeRule.setContent {
            ZhixingTheme {
                CreateTaskDialog(onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag("TaskTitleInput").assertIsDisplayed()
        composeRule.onNodeWithText("确认").assertIsDisplayed()
        composeRule.onNodeWithText("取消").assertIsDisplayed()
    }

    @Test
    fun confirm_invokes_onConfirm_with_input_text() {
        var confirmedTitle = ""
        composeRule.setContent {
            ZhixingTheme {
                CreateTaskDialog(
                    onConfirm = { confirmedTitle = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag("TaskTitleInput").performTextInput("我的新任务")
        composeRule.onNodeWithText("确认").performClick()

        assert(confirmedTitle == "我的新任务") { "Expected '我的新任务' but was '$confirmedTitle'" }
    }

    @Test
    fun cancel_invokes_onDismiss() {
        var dismissed = false
        composeRule.setContent {
            ZhixingTheme {
                CreateTaskDialog(
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeRule.onNodeWithText("取消").performClick()

        assert(dismissed) { "Expected onDismiss to be called" }
    }
}
