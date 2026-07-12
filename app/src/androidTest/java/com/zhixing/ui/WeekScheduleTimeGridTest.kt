package com.zhixing.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeRule.onNodeWithText("11:00-12:00", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun click_week_block_then_unschedule_invokes_onUnscheduleSubproject() {
        // 周视图完整回退流程：点击已排期块 → 菜单「回退」→ onUnscheduleSubproject 被调用。
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(
                ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                    subprojectStatus = "已排期", subprojectId = 1),
            ),
        )

        var unscheduledId: Long? = null

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = itemsByDate,
                    onUnscheduleSubproject = { unscheduledId = it },
                )
            }
        }

        composeRule.onNodeWithTag("WeekItem-1", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("回退").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { unscheduledId != null }
        assertThat(unscheduledId).isEqualTo(1L)
    }

    @Test
    fun click_week_block_menu_shows_complete_and_abandon_too() {
        // 周视图块菜单应同时提供完成 / 放弃 / 回退三个操作。
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

        composeRule.onNodeWithTag("WeekItem-1", useUnmergedTree = true).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("完成").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("完成").assertIsDisplayed()
        composeRule.onNodeWithText("放弃").assertIsDisplayed()
        composeRule.onNodeWithText("回退").assertIsDisplayed()
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

    @Test
    fun click_week_backlog_item_then_schedule_invokes_onScheduleSubproject() {
        // 周视图完整排期流程：点 backlog 药丸 → 菜单「排期」→ 排期对话框确认 → onScheduleSubproject 被调用。
        // onScheduleSubproject 周视图签名为 (subprojectId, date, startTime, endTime)。
        val backlog = listOf(BacklogItem(id = 2, title = "划重点", estimatedDuration = 60))

        var scheduledArgs: Quadruple<Long, String, Int, Int>? = null

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = emptyMap(),
                    backlogItems = backlog,
                    onScheduleSubproject = { id, date, start, end ->
                        scheduledArgs = Quadruple(id, date, start, end)
                    },
                )
            }
        }

        // 展开 backlog 面板后，点击 backlog 药丸 → 弹出操作菜单
        composeRule.onNodeWithTag("BacklogItem-2").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogScheduleConfirm").fetchSemanticsNodes().isNotEmpty()
        }
        // 点「排期」→ 排期日期时间选择对话框
        composeRule.onNodeWithTag("BacklogScheduleConfirm").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ConfirmScheduleTime").fetchSemanticsNodes().isNotEmpty()
        }
        // 直接确认（默认日期/时间段合法）
        composeRule.onNodeWithTag("ConfirmScheduleTime").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { scheduledArgs != null }
        assertThat(scheduledArgs?.first).isEqualTo(2L)
        // 默认排期日期应在本周范围内
        assertThat(scheduledArgs?.second).isIn(weekDates)
        assertThat(scheduledArgs?.third).isEqualTo(540)   // 09:00
        assertThat(scheduledArgs?.fourth).isEqualTo(600)   // 10:00 (09:00 + 60min)
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
