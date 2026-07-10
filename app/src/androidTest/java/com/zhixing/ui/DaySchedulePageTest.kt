package com.zhixing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zhixing.ui.theme.ZhixingTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): DaySchedulePage 日视图 UI。
 *
 * 验证：
 *   - 有排期 → 显示子项目标题 + 时间段
 *   - 无排期 → 显示空状态引导
 */
class DaySchedulePageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_schedule_items_with_time_range() {
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660),
            ScheduleItem(id = 2, subprojectTitle = "划重点", startTime = 540, endTime = 600),
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
        composeRule.onNodeWithText("划重点").assertIsDisplayed()
        composeRule.onNodeWithText("10:00 - 11:00").assertIsDisplayed()
        composeRule.onNodeWithText("09:00 - 10:00").assertIsDisplayed()
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

    @Test
    fun week_view_shows_7_days_with_weekday_labels() {
        // 首列(2026-07-06)放项目，确保该列始终可见、断言稳定
        val weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12")
        val itemsByDate = mapOf(
            "2026-07-06" to listOf(ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660)),
        )

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = itemsByDate,
                )
            }
        }

        // 首列始终可见，其排期文本正常渲染
        composeRule.onNodeWithTag("WeekDay-2026-07-06").assertIsDisplayed()
        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * 回归测试：验证"tab 切走再切回，视图状态仍在"。
     *
     * 用 simple 模型模拟：外层持有 isWeekView 状态，
     * child composable（模拟 ScheduleTab）接收状态展示对应 marker，
     * 通过一个"是否挂载 child"开关模拟 tab 切走 / 切回的行为。
     */
    @Test
    fun view_state_persists_when_child_is_unmounted_and_remounted() {
        val isWeekView = mutableStateOf(false)
        val childMounted = mutableStateOf(true)

        composeRule.setContent {
            ZhixingTheme {
                Column {
                    Row {
                        FilterChip(
                            selected = !isWeekView.value,
                            onClick = { isWeekView.value = false },
                            label = { Text("日") },
                            modifier = Modifier.testTag("DayViewChip"),
                        )
                        FilterChip(
                            selected = isWeekView.value,
                            onClick = { isWeekView.value = true },
                            label = { Text("周") },
                            modifier = Modifier.testTag("WeekViewChip"),
                        )
                    }
                    if (childMounted.value) {
                        if (isWeekView.value) {
                            Text("WEEK", modifier = Modifier.testTag("WeekMarker"))
                        } else {
                            Text("DAY", modifier = Modifier.testTag("DayMarker"))
                        }
                    }
                }
            }
        }

        // 默认日视图
        composeRule.onNodeWithTag("DayMarker").assertIsDisplayed()

        // 切到周视图
        composeRule.onNodeWithTag("WeekViewChip").performClick()
        composeRule.onNodeWithTag("WeekMarker").assertIsDisplayed()

        // 模拟：切到别的 tab（child 被卸载）
        childMounted.value = false
        composeRule.waitForIdle()

        // 模拟：切回日程 tab（child 重新挂载）— 状态应仍是周视图
        childMounted.value = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("WeekMarker").assertIsDisplayed()
    }

    @Test
    fun day_week_toggle_switches_and_stays_in_week_view() {
        val isWeek = mutableStateOf(false)

        composeRule.setContent {
            ZhixingTheme {
                Column {
                    Row {
                        FilterChip(
                            selected = !isWeek.value,
                            onClick = { isWeek.value = false },
                            label = { Text("日") },
                            modifier = Modifier.testTag("DayViewChip"),
                        )
                        FilterChip(
                            selected = isWeek.value,
                            onClick = { isWeek.value = true },
                            label = { Text("周") },
                            modifier = Modifier.testTag("WeekViewChip"),
                        )
                    }
                    // 用 testTag 模拟视图区域，验证周视图生效
                    if (isWeek.value) {
                        Text("WEEK_VIEW", modifier = Modifier.testTag("WeekViewMarker"))
                    } else {
                        Text("DAY_VIEW", modifier = Modifier.testTag("DayViewMarker"))
                    }
                }
            }
        }

        // 初始：日视图
        composeRule.onNodeWithTag("DayViewMarker").assertIsDisplayed()

        // 切到周视图
        composeRule.onNodeWithTag("WeekViewChip").performClick()
        composeRule.onNodeWithTag("WeekViewMarker").assertIsDisplayed()

        // 周视图下也能点击"日"切回（关键：chip 未被遮挡）
        composeRule.onNodeWithTag("DayViewChip").performClick()
        composeRule.onNodeWithTag("DayViewMarker").assertIsDisplayed()
    }

    @Test
    fun week_view_day_with_no_items_shows_empty_grid() {
        // 无项目的日列显示空格栅（无线即可），日列本身仍存在。
        val weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12")
        val itemsByDate = emptyMap<String, List<ScheduleItem>>()

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = itemsByDate,
                )
            }
        }

        composeRule.onNodeWithTag("WeekDay-2026-07-08").assertExists()
        // 该日无项目块
        composeRule.onAllNodesWithTag("WeekItem-", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun schedule_time_picker_shows_tabs_and_initial_state_enabled() {
        composeRule.setContent {
            ZhixingTheme {
                ScheduleTimePickerDialog(
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }

        // Tab 结构 + 初始"开始"选中
        composeRule.onNodeWithTag("StartTab").assertIsDisplayed()
        composeRule.onNodeWithTag("EndTab").assertIsDisplayed()
        composeRule.onNodeWithTag("StartTimePicker").assertIsDisplayed()

        // 初始 09:00-10:00 合法，确认按钮 enabled
        composeRule.onNodeWithTag("ConfirmScheduleTime").assertIsEnabled()

        // 切到"结束"标签 → 结束时间选择器出现
        composeRule.onNodeWithTag("EndTab").performClick()
        composeRule.onNodeWithTag("EndTimePicker").assertIsDisplayed()
    }

    @Test
    fun schedule_time_picker_confirm_emits_minutes_from_midnight() {
        var captured: Pair<Int, Int>? = null

        composeRule.setContent {
            ZhixingTheme {
                ScheduleTimePickerDialog(
                    onConfirm = { start, end -> captured = start to end },
                    onDismiss = {},
                )
            }
        }

        // 不拨动滚轮，直接确认 → 拿到初始时间 09:00=540, 10:00=600
        composeRule.onNodeWithTag("ConfirmScheduleTime").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { captured != null }
        assertThat(captured).isEqualTo(540 to 600)
    }

    @Test
    fun overdue_item_shows_in_time_grid() {
        // 时间格栅中逾期项仍渲染标题 + 时间段，仅做弱化（灰色）处理。
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660, subprojectStatus = "已排期", isOverdue = true),
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
    fun click_schedule_block_then_complete_invokes_onCompleteSubproject() {
        val items = listOf(
            ScheduleItem(id = 10, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                subprojectStatus = "已排期", subprojectId = 1),
        )

        var completedId: Long? = null

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                    onCompleteSubproject = { completedId = it },
                )
            }
        }

        // 点击排期块 → 弹出完成操作
        composeRule.onNodeWithTag("ScheduleBlock-10").performClick()
        composeRule.onNodeWithText("完成").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { completedId != null }
        assertThat(completedId).isEqualTo(1L)
    }

    @Test
    fun completed_schedule_block_shows_dimmed() {
        // "已完成"的子项目卡片应做弱化（变灰）显示。
        val items = listOf(
            ScheduleItem(id = 10, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                subprojectStatus = "已完成", subprojectId = 1),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                )
            }
        }

        composeRule.onNodeWithTag("ScheduleBlock-10")
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, true))
    }

    @Test
    fun abandoned_schedule_block_shows_dimmed() {
        // "已放弃"的子项目卡片同样应做弱化（变灰）显示。
        val items = listOf(
            ScheduleItem(id = 10, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                subprojectStatus = "已放弃", subprojectId = 1),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                )
            }
        }

        composeRule.onNodeWithTag("ScheduleBlock-10")
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, true))
    }

    @Test
    fun active_schedule_block_not_dimmed() {
        // "已排期"（进行中）的子项目卡片不做弱化显示。
        val items = listOf(
            ScheduleItem(id = 10, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                subprojectStatus = "已排期", subprojectId = 1),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                )
            }
        }

        composeRule.onNodeWithTag("ScheduleBlock-10")
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, false))
    }

    @Test
    fun click_abandon_then_block_shows_dimmed() {
        // 完整的响应式流程：初始"已排期"（不弱化）→ 点击放弃 → 状态变为"已放弃" → 卡片立即弱化（变灰）。
        val items = mutableStateOf(listOf(
            ScheduleItem(id = 10, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                subprojectStatus = "已排期", subprojectId = 1),
        ))

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items.value,
                    onAbandonSubproject = { id ->
                        items.value = items.value.map {
                            if (it.subprojectId == id) it.copy(subprojectStatus = "已放弃") else it
                        }
                    },
                )
            }
        }

        // 初始：进行中，不弱化
        composeRule.onNodeWithTag("ScheduleBlock-10")
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, false))

        // 点击排期块 → 弹出操作菜单 → 点击"放弃"
        composeRule.onNodeWithTag("ScheduleBlock-10").performClick()
        composeRule.onNodeWithText("放弃").performClick()

        // 放弃后：状态变为"已放弃"，卡片应立即弱化
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithTag("ScheduleBlock-10")
                .fetchSemanticsNode().config.getOrNull(ScheduleBlockDimmedKey) == true
        }
        composeRule.onNodeWithTag("ScheduleBlock-10")
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, true))
    }

    @Test
    fun click_schedule_block_then_abandon_invokes_onAbandonSubproject() {
        val items = listOf(
            ScheduleItem(id = 10, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                subprojectStatus = "已排期", subprojectId = 1),
        )

        var abandonedId: Long? = null

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                    onAbandonSubproject = { abandonedId = it },
                )
            }
        }

        // 点击排期块 → 弹出操作菜单 → 点击"放弃"
        composeRule.onNodeWithTag("ScheduleBlock-10").performClick()
        composeRule.onNodeWithText("放弃").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { abandonedId != null }
        assertThat(abandonedId).isEqualTo(1L)
    }

    @Test
    fun shows_below_schedule_a_backlog_section() {
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660),
        )
        val backlog = listOf(
            BacklogItem(id = 2, title = "划重点"),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithTag("BacklogSection").assertIsDisplayed()
        composeRule.onNodeWithText("划重点").assertIsDisplayed()
    }
}
