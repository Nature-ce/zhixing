package com.zhixing.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.zhixing.ui.theme.ZhixingTheme
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.Rule
import org.junit.Test

/**
 * instrumented 测试：WeekSchedulePage 时间格栅渲染。
 *
 * 验证时间格栅特有结构：
 *   - 左侧时间轴标签（06:00）
 *   - 项目块显示时间段（10:00 - 11:00）
 *   - 周日期列按 weekDates 渲染
 */
class WeekScheduleTimeGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val weekDates = listOf(
        "2026-07-06", "2026-07-07", "2026-07-08",
        "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12",
    )

    @Test
    fun shows_item_with_title_and_time_in_its_day_column() {
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(
                ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660),
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

        composeRule.onNodeWithTag("WeekDay-2026-07-06").assertExists()
        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
        // 时间格栅特有：项目块有独立的 testTag
        composeRule.onNodeWithTag("WeekItem-1", useUnmergedTree = true).assertExists()
    }

    @Test
    fun shows_time_axis_label() {
        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = emptyMap(),
                )
            }
        }

        // 时间格栅特有：左侧时间轴标签 06:00
        composeRule.onNodeWithText("06:00").assertIsDisplayed()
    }

    @Test
    fun shows_item_with_full_time_range() {
        // 周视图与日视图一致，应显示完整时间段 "11:00 - 12:00"，而非仅起始 "11:00"
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(
                ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 660, endTime = 720),
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

        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("11:00 - 12:00", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun block_vertical_position_matches_time_axis_label() {
        // 9:00 排期块的顶部，必须与左侧时间轴 "09:00" 标签对齐（不能错位到 08:30）
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(
                ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 540, endTime = 600),
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

        composeRule.waitForIdle()

        val itemTop = composeRule
            .onNodeWithTag("WeekItem-1", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val labelTop = composeRule
            .onNodeWithText("09:00")
            .fetchSemanticsNode()
            .boundsInRoot.top

        // 允许小块 padding(vertical=1dp) 带来的几像素差异，但必须远小于一行(48dp≈132px)
        assertThat(itemTop).isCloseTo(labelTop, within(8.0f))
    }

    @Test
    fun abandoned_week_item_shows_dimmed() {
        // 周视图里"已放弃"的子项目卡片应做弱化显示（与日视图行为一致）。
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(
                ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                    subprojectStatus = "已放弃", subprojectId = 1),
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

        composeRule.onNodeWithTag("WeekItem-1", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, true))
    }

    @Test
    fun active_week_item_not_dimmed() {
        // 周视图里"已排期"（进行中）的子项目卡片不做弱化显示。
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(
                ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                    subprojectStatus = "已排期", subprojectId = 1),
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

        composeRule.onNodeWithTag("WeekItem-1", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, false))
    }

    @Test
    fun shows_date_column_for_each_day() {
        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = emptyMap(),
                )
            }
        }

        composeRule.onNodeWithTag("WeekDay-2026-07-06").assertExists()
        composeRule.onNodeWithTag("WeekDay-2026-07-08").assertExists()
    }
}
