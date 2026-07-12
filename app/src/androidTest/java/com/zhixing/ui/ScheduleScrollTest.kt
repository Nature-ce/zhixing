package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * instrumented 回归测试：时间格栅的纵向滚动。
 *
 * 验证日程页在排期项位于晚时段（21:00）时，
 * 可通过滚动将其带入视口 — 这是本次修复的核心行为。
 */
class ScheduleScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun day_view_late_item_can_be_scrolled_into_view() {
        // 21:00 = 1260 min，靠近格栅底部（06:00-22:30, 共 34 行, 总高 1632dp）
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "晚自习", startTime = 1260, endTime = 1320),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                )
            }
        }

        // 滚动前：节点存在但不在视口
        composeRule.onNodeWithTag("ScheduleBlock-1").assertExists()

        // 执行滚动 → 项目进入视口
        composeRule.onNodeWithTag("ScheduleBlock-1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("晚自习", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("21:00-22:00").assertIsDisplayed()
    }

    @Test
    fun day_view_early_item_visible_without_scroll() {
        // 10:00 在视口上部，无需滚动即可见
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

        composeRule.onNodeWithTag("ScheduleBlock-1").assertIsDisplayed()
        composeRule.onNodeWithText("10:00-11:00").assertIsDisplayed()
    }

    @Test
    fun week_view_late_item_can_be_scrolled_into_view() {
        val weekDates = listOf(
            "2026-07-06", "2026-07-07", "2026-07-08",
            "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12",
        )
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(
                ScheduleItem(id = 1, subprojectTitle = "晚自习", startTime = 1260, endTime = 1320),
            ),
        )

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = itemsByDate,
                )
            }
        }

        // 项目在纵向远处；模拟真实纵向滑动手势（swipeUp 路由到纵向滚动容器）
        composeRule.onNodeWithTag("WeekItem-1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("WeekDay-2026-07-06", useUnmergedTree = true)
            .performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("晚自习", useUnmergedTree = true).assertIsDisplayed()
    }
}
