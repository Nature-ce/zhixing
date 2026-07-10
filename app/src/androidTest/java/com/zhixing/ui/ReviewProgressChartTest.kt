package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * instrumented 测试：ReviewProgressChart 时间线渲染。
 *
 * 验证：
 *   - 顶部整体进度数字"已完成 N/M"
 *   - 每个子项目行显示标题
 *   - 终态子项目显示格式化完成日期
 *   - 未完成的子项目显示"—"
 */
class ReviewProgressChartTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val result = TimelineResult(
        completedCount = 2,
        totalCount = 3,
        items = listOf(
            TimelineItem(id = 1, title = "选书目", icon = TimelineIcon.CHECK, status = "已完成", completedDate = "2026-07-08"),
            TimelineItem(id = 2, title = "划重点", icon = TimelineIcon.ABANDON, status = "已放弃", completedDate = "2026-07-09"),
            TimelineItem(id = 3, title = "写总结", icon = TimelineIcon.PENDING, status = "已排期", completedDate = null),
        ),
    )

    @Test
    fun shows_overall_progress() {
        composeRule.setContent {
            ZhixingTheme {
                ReviewProgressChart(result)
            }
        }

        composeRule.onNodeWithText("已完成 2/3").assertIsDisplayed()
    }

    @Test
    fun shows_each_subproject_title() {
        composeRule.setContent {
            ZhixingTheme {
                ReviewProgressChart(result)
            }
        }

        composeRule.onNodeWithText("选书目").assertIsDisplayed()
        composeRule.onNodeWithText("划重点").assertIsDisplayed()
        composeRule.onNodeWithText("写总结").assertIsDisplayed()
    }

    @Test
    fun shows_completedDate_for_terminal_items() {
        composeRule.setContent {
            ZhixingTheme {
                ReviewProgressChart(result)
            }
        }

        composeRule.onNodeWithText("2026-07-08").assertIsDisplayed()
        composeRule.onNodeWithText("2026-07-09").assertIsDisplayed()
    }

    @Test
    fun shows_placeholder_for_non_terminal_items() {
        composeRule.setContent {
            ZhixingTheme {
                ReviewProgressChart(result)
            }
        }

        // 未完成的子项目显示"—"
        composeRule.onNodeWithText("—").assertIsDisplayed()
    }

    @Test
    fun exposes_progress_bar_test_tag() {
        composeRule.setContent {
            ZhixingTheme {
                ReviewProgressChart(result)
            }
        }

        composeRule.onNodeWithTag("ProgressBar").assertIsDisplayed()
    }
}
