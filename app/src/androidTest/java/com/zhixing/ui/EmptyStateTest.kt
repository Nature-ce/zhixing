package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): 空状态欢迎页。
 *
 * CONTEXT.md: 第一次打开 app（无任何数据）应显示欢迎文案 + 创建第一个任务按钮。
 *
 * 测试纯 UI composable（EmptyState），不启动 Activity、不触数据库。
 */
class EmptyStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun empty_state_shows_welcome_message_and_create_button() {
        composeRule.setContent {
            ZhixingTheme {
                EmptyState(onCreateFirstTask = {})
            }
        }

        composeRule.onNodeWithTag("EmptyStateTitle").assertIsDisplayed()
        composeRule.onNodeWithText("创建第一个任务").assertIsDisplayed()
    }
}
