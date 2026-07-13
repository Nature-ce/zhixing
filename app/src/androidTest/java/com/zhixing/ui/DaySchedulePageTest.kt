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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
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
        composeRule.onNodeWithText("10:00-11:00").assertIsDisplayed()
        composeRule.onNodeWithText("09:00-10:00").assertIsDisplayed()
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
    fun schedule_time_picker_shows_start_picker_and_end_preview() {
        // 结束时间 = 开始 + 建议时长（默认 60min），自动回显，无需 tab 切换结束滚轮。
        composeRule.setContent {
            ZhixingTheme {
                ScheduleTimePickerDialog(
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }

        // 仅展示开始时间选择器，无"结束"tab / 结束滚轮
        composeRule.onNodeWithTag("StartTimePicker").assertIsDisplayed()
        composeRule.onAllNodesWithTag("StartTab").assertCountEquals(0)
        composeRule.onAllNodesWithTag("EndTab").assertCountEquals(0)

        // 结束预览 = 09:00 + 60min = 10:00
        composeRule.onNodeWithTag("ScheduleEndTimePreview").assertTextEquals("10:00")

        // 初始合法，确认按钮 enabled
        composeRule.onNodeWithTag("ConfirmScheduleTime").assertIsEnabled()
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
        // 时间格栅中逾期项仍渲染标题 + 时间段；未弱化（保持黑色），与"已完成/已放弃"区分。
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
        composeRule.onNodeWithText("10:00-11:00").assertIsDisplayed()
        // 逾期项：弱化（灰）+ 灰色小闹钟图标
        composeRule.onNodeWithContentDescription("逾期").assertIsDisplayed()
        composeRule.onNodeWithTag("ScheduleBlock-1", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(ScheduleBlockDimmedKey, true))
    }

    @Test
    fun non_overdue_item_shows_no_overdue_icon() {
        // 未逾期的已排期项，不该出现逾期图标。
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 660, subprojectStatus = "已排期", isOverdue = false),
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
        composeRule.onNodeWithContentDescription("逾期").assertDoesNotExist()
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
    fun completed_schedule_block_shows_check_icon() {
        // "已完成"的子项目卡片除弱化显示外，还应挂绿色钩（Icons.Filled.Check）一眼标识终态。
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

        composeRule.onNodeWithContentDescription("已完成").assertIsDisplayed()
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
    fun click_schedule_block_then_abandon_removes_it() {
        // 完整流程：初始"已排期"（渲染块）→ 点击放弃 → 块直接消失（放弃 = 删除，不显示弱化占位块）。
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
                        // 放弃 = 彻底删除：把该子项目从列表里移除（模拟 VM 删子项目 + 清排期 → 块消失）。
                        items.value = items.value.filterNot { it.subprojectId == id }
                    },
                )
            }
        }

        // 初始：块存在
        composeRule.onNodeWithTag("ScheduleBlock-10").assertExists()

        // 点击排期块 → 弹出操作菜单 → 点击"放弃"
        composeRule.onNodeWithTag("ScheduleBlock-10").performClick()
        composeRule.onNodeWithText("放弃").performClick()

        // 放弃后：块直接消失
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ScheduleBlock-10").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("ScheduleBlock-10").assertDoesNotExist()
    }

    @Test
    fun click_schedule_block_then_unschedule_invokes_onUnscheduleSubproject() {
        // 完整的回退流程：点击已排期块 → 菜单「回退」→ onUnscheduleSubproject 被调用。
        val items = listOf(
            ScheduleItem(id = 10, subprojectTitle = "选书目", startTime = 600, endTime = 660,
                subprojectStatus = "已排期", subprojectId = 1),
        )

        var unscheduledId: Long? = null

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = items,
                    onUnscheduleSubproject = { unscheduledId = it },
                )
            }
        }

        // 点击排期块 → 弹出操作菜单 → 点击「回退」
        composeRule.onNodeWithTag("ScheduleBlock-10").performClick()
        composeRule.onNodeWithText("回退").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { unscheduledId != null }
        assertThat(unscheduledId).isEqualTo(1L)
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
    fun click_backlog_item_expands_inline_panel() {
        // 新交互：点 backlog 药丸不再弹 AlertDialog，而是展开 inline 编辑面板：
        //   - 名称输入框（预填原标题）
        //   - 预期时间输入框（预填原时长）
        //   - 「排期」按钮
        val backlog = listOf(BacklogItem(id = 2, title = "划重点", estimatedDuration = 60))

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithTag("BacklogItem-2").performClick()

        // inline 面板展开：名称 / 预期时间 / 排期按钮均可见
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanel-2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("BacklogPanelName-2").assertTextContains("划重点")
        composeRule.onNodeWithTag("BacklogPanelDuration-2").assertTextContains("60")
        composeRule.onNodeWithTag("BacklogPanelSchedule-2").assertIsDisplayed()
        // 面板含「取消」次级按钮，点它应可收起
        composeRule.onNodeWithTag("BacklogPanelCancel-2").assertIsDisplayed()

        // 旧 AlertDialog 不再出现
        composeRule.onNodeWithTag("BacklogScheduleConfirm").assertDoesNotExist()
    }

    @Test
    fun click_cancel_on_panel_collapses_it() {
        // 面板底部「取消」按钮 → 收起面板。
        val backlog = listOf(BacklogItem(id = 2, title = "划重点", estimatedDuration = 60))

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithTag("BacklogItem-2").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanel-2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("BacklogPanelCancel-2").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanel-2").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("BacklogPanel-2").assertDoesNotExist()
        composeRule.onNodeWithTag("BacklogPanelScrim").assertDoesNotExist()
    }

    @Test
    fun editing_backlog_panel_name_invokes_onUpdateSubproject() {
        // 持久化：在 inline 面板里编辑名称 → onUpdateSubproject 被回调。
        val backlog = listOf(BacklogItem(id = 2, title = "划重点", estimatedDuration = 60))

        var updatedArgs: Triple<Long, String, Int?>? = null

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                    onUpdateSubproject = { id, title, dur -> updatedArgs = Triple(id, title, dur) },
                )
            }
        }

        composeRule.onNodeWithTag("BacklogItem-2").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanelName-2").fetchSemanticsNodes().isNotEmpty()
        }

        // 清空后输入新名称 → 持久化回调携带新名称。
        composeRule.onNodeWithTag("BacklogPanelName-2").performTextReplacement("看第九章")

        composeRule.waitUntil(timeoutMillis = 5_000) {
            updatedArgs != null && updatedArgs?.second == "看第九章"
        }
        assertThat(updatedArgs?.first).isEqualTo(2L)
        assertThat(updatedArgs?.second).isEqualTo("看第九章")
    }

    @Test
    fun confirm_schedule_from_backlog_menu_invokes_onScheduleSubproject() {
        // 新交互完整排期流程：点 backlog 药丸 → 展开 inline 面板 → 点面板「排期」
        // → 排期对话框确认 → onScheduleSubproject 被调用。
        // 子项目建议时长 60min，故结束 = 开始 09:00 + 60min = 10:00。
        val backlog = listOf(BacklogItem(id = 2, title = "划重点", estimatedDuration = 60))

        var scheduledArgs: Triple<Long, Int, Int>? = null

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                    onScheduleSubproject = { id, start, end -> scheduledArgs = Triple(id, start, end) },
                )
            }
        }

        // 点 backlog 药丸 → 展开 inline 面板 → 点面板「排期」（BacklogPanelSchedule-2）
        composeRule.onNodeWithTag("BacklogItem-2").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanelSchedule-2").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("BacklogPanelSchedule-2").performClick()

        // 排期对话框弹出（默认 09:00-10:00 合法）→ 直接确认
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ConfirmScheduleTime").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ConfirmScheduleTime").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { scheduledArgs != null }
        assertThat(scheduledArgs?.first).isEqualTo(2L)
        assertThat(scheduledArgs?.second).isEqualTo(540)   // 09:00
        assertThat(scheduledArgs?.third).isEqualTo(600)    // 10:00
    }

    @Test
    fun editing_duration_in_panel_reflects_in_schedule_end_preview() {
        // 行为契约：在 inline 面板里把预期时间从 60 改成 90，
        // 排期对话框的结束预览应即时反映新值（09:00 + 90min = 10:30），
        // 而不是沿用默认 60 算出的 10:00。
        val backlog = listOf(BacklogItem(id = 2, title = "划重点", estimatedDuration = 60))

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                    onScheduleSubproject = { _, _, _ -> },
                )
            }
        }

        // 点 backlog 药丸 → 展开 inline 面板
        composeRule.onNodeWithTag("BacklogItem-2").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanelDuration-2").fetchSemanticsNodes().isNotEmpty()
        }

        // 把预期时间从 60 改成 90
        composeRule.onNodeWithTag("BacklogPanelDuration-2").performTextReplacement("90")

        // 点面板「排期」→ 打开排期对话框
        composeRule.onNodeWithTag("BacklogPanelSchedule-2").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ScheduleEndTimePreview").fetchSemanticsNodes().isNotEmpty()
        }

        // 结束预览应反映面板里新编辑的 90min：09:00 + 90 = 10:30
        composeRule.onNodeWithTag("ScheduleEndTimePreview").assertTextEquals("10:30")
    }

    @Test
    fun click_blank_area_outside_panel_collapses_it() {
        // 新行为：面板以屏幕中央弹窗形式出现，点击面板外的空白遮罩 → 面板收起。
        val backlog = listOf(BacklogItem(id = 2, title = "划重点", estimatedDuration = 60))

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-08",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithTag("BacklogItem-2").performClick()

        // 面板展开（屏幕中央弹窗）
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanel-2").fetchSemanticsNodes().isNotEmpty()
        }
        // 全屏遮罩存在
        composeRule.onNodeWithTag("BacklogPanelScrim").assertIsDisplayed()

        // 点击遮罩上方面板外的空白区域 → 面板收起
        composeRule.onNodeWithTag("BacklogPanelScrim").performTouchInput {
            click(Offset(5f, 5f))
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanel-2").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("BacklogPanel-2").assertDoesNotExist()
        composeRule.onNodeWithTag("BacklogPanelScrim").assertDoesNotExist()
    }

    @Test
    fun short_item_shows_time_inline_to_the_right_of_title() {
        // 10min 项目也应把时间段显示在标题右侧同行（新格式 HH:MM-HH:MM），
        // 让用户一眼看出该时段被何项目占据。
        val items = listOf(
            ScheduleItem(id = 1, subprojectTitle = "选书目", startTime = 600, endTime = 610),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(date = "2026-07-08", scheduleItems = items)
            }
        }

        composeRule.onNodeWithText("选书目").assertIsDisplayed()
        composeRule.onNodeWithText("10:00-10:10").assertIsDisplayed()
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
