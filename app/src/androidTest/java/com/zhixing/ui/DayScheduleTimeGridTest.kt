package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * instrumented 测试：DaySchedulePage 时间格栅渲染。
 *
 * 验证：
 *   - 时间段项目显示时间文字（10:00 - 11:00）
 *   - 时间轴标签（06:00、10:00）可见
 *   - 无项目时显示空状态
 */
class DayScheduleTimeGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_schedule_item_with_time_range() {
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                )
            }
        }

        composeRule.onNodeWithText("选书目").assertIsDisplayed()
        composeRule.onNodeWithText("10:00 - 11:00").assertIsDisplayed()
    }

    @Test
    fun shows_time_axis_labels() {
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                )
            }
        }

        // 起始时间标签可见
        composeRule.onNodeWithText("06:00").assertIsDisplayed()
    }

    @Test
    fun empty_schedule_shows_empty_state() {
        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = emptyList(),
                    backlogItems = emptyList(),
                )
            }
        }

        composeRule.onNodeWithTag("ScheduleEmptyState").assertIsDisplayed()
    }
}
