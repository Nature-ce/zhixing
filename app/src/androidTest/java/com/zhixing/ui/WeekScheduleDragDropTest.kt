package com.zhixing.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zhixing.ui.theme.ZhixingTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * instrumented 测试：周视图拖放排期的 UI 结构。
 *
 * 验证：
 *   - backlog 子项目渲染为 drag source（testTag "BacklogItem-${id}"）
 *   - 7 天列渲染为 drop target（testTag "WeekDay-${date}"）
 *   - 拖放后回调 onScheduleSubproject 被调用（日期由落列决定，时间由落点决定）
 */
class WeekScheduleDragDropTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backlog_items_render_as_drag_sources() {
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目"),
            BacklogItem(id = 2, title = "划重点"),
        )

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("BacklogItem-2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun day_columns_render_as_drop_targets() {
        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = listOf(BacklogItem(id = 1, title = "选书目")),
                )
            }
        }

        // 7 天列都渲染为 drop target
        listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12").forEach { date ->
            composeRule.onNodeWithTag("WeekDay-$date", useUnmergedTree = true).assertExists()
        }
    }

    /**
     * 行为 #4（周视图镜像）：backlog 子项目按父任务归组，每组有独立 header 显示任务标题；
     * 药丸不再在内部印任务标题，而是作为组 header 出现。
     */
    @Test
    fun backlog_groups_subprojects_by_task() {
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目", taskId = 10L, taskTitle = "读书笔记"),
            BacklogItem(id = 2, title = "划重点", taskId = 10L, taskTitle = "读书笔记"),
            BacklogItem(id = 3, title = "挑礼物", taskId = 20L, taskTitle = "筹备婚礼"),
        )

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithTag("TaskGroupHeader-10", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("TaskGroupHeader-20", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("读书笔记", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("筹备婚礼", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("BacklogItem-2", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("BacklogItem-3", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * 行为 #5（周视图镜像）：点击组 header 折叠/展开该组的子项目药丸。
     * 折叠"读书笔记"组 → 其药丸隐藏，"筹备婚礼"组药丸仍可见；再点 → 恢复。
     */
    @Test
    fun collapsing_taskGroup_hidesItsPills() {
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目", taskId = 10L, taskTitle = "读书笔记"),
            BacklogItem(id = 2, title = "挑礼物", taskId = 20L, taskTitle = "筹备婚礼"),
        )

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("挑礼物", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("TaskGroupHeader-10", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("选书目", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("挑礼物", useUnmergedTree = true).assertIsDisplayed()
        // 折叠态组头不显示向右箭头（"展开"），仿照整体 backlog Header
        composeRule.onAllNodesWithContentDescription("展开", useUnmergedTree = true).assertCountEquals(0)

        composeRule.onNodeWithTag("TaskGroupHeader-10", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * 周视图展开态 header：有"待排期"标题 + "收起"向下箭头，无计数徽标（badge）。
     *
     * 行为 #1（周视图镜像）：Badge（未排期个数）已被移除。
     */
    @Test
    fun backlog_rendersPillsWithHeaderAndBadge() {
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
            BacklogItem(id = 2, title = "划重点"),
        )

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = backlog,
                )
            }
        }

        // 标题 + 向下箭头（收起）
        composeRule.onNodeWithText("待排期", useUnmergedTree = false).assertIsDisplayed()
        // 整体 backlog header + 各组 header 均可能带"收起"chevron，断言至少一个可见
        composeRule.onAllNodesWithContentDescription("收起", useUnmergedTree = true).onFirst().assertIsDisplayed()
        // 计数徽标 "2" 不应存在
        composeRule.onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(0)
        // 药丸标题
        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("划重点", useUnmergedTree = true).assertIsDisplayed()
        // 时长标签（药丸副标题）
        composeRule.onNodeWithText("60分", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * 周视图 backlog 折叠由外部 prop 驱动（行为 #3/#4）：
     *   - collapsed=true 时药丸隐藏、header 仍在
     *   - 点击 header 调用 onCollapsedChange(!collapsed)，外部 state 翻转后页面跟着折叠
     */
    @Test
    fun backlog_canBeCollapsed_andRestored() {
        val weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12")
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目"),
            BacklogItem(id = 2, title = "划重点"),
        )

        composeRule.setContent {
            ZhixingTheme {
                // 外部持有折叠状态（模拟 MainScreen 层级的 collapsedBacklog）。
                var externalCollapsed by remember { mutableStateOf(false) }
                WeekSchedulePage(
                    weekDates = weekDates,
                    itemsByDate = emptyMap(),
                    backlogItems = backlog,
                    collapsed = externalCollapsed,
                    onCollapsedChange = { externalCollapsed = it },
                )
            }
        }

        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("划重点", useUnmergedTree = true).assertIsDisplayed()

        // 点 header → onCollapsedChange(true) → 外部状态翻转为 true → 折叠
        composeRule.onNodeWithTag("BacklogHeader", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("选书目", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("划重点", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithTag("BacklogHeader", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun dragBackToBacklog_cancelsSchedule() {
        val captured = AtomicReference<Quad<Long, String, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = listOf(
                        BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
                    ),
                    onScheduleSubproject = { id, date, start, end ->
                        captured.set(Quad(id, date, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        // 长按拖起 → 向格栅方向移（负 localY，格栅在上方）→ 再拖回 backlog（正 localY，越过起点向下）→ 释放
        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(Offset(center.x, center.y - 400f), delayMillis = 800L)
            moveTo(Offset(center.x, center.y + 100f))
            up()
        }

        composeRule.waitForIdle()

        // 释放回 backlog 区域 → 不触发排期
        assertThat(captured.get()).isNull()
    }

    @Test
    fun longPressDrag_fromBacklogIntoDayColumn_schedulesAtCorrectDateAndTime() {
        // 捕获回调参数：Quad<subprojectId, date, start, end>
        val captured = AtomicReference<Quad<Long, String, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = listOf(
                        BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
                    ),
                    onScheduleSubproject = { id, date, start, end ->
                        captured.set(Quad(id, date, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        // 目标：2026-07-09 列（index 3），第 6 行 = 09:00 = 540。
        // 注意：目标列在屏幕外，其 boundsInRoot 不可靠。
        // 列索引法下，落点 x = 首列 left + index × colWidth + colWidth/2，
        // 故取一个落在第 3 列内的 root x 即可：首列 left + (3 + 0.5) × colWidth。
        val targetDate = "2026-07-09"
        val firstDayColumn = composeRule.onNodeWithTag("WeekDay-2026-07-06", useUnmergedTree = true)
            .fetchSemanticsNode()
        val colWidthPx = firstDayColumn.size.width.toFloat()
        val firstColLeft = firstDayColumn.boundsInRoot.left
        val colTop = firstDayColumn.boundsInRoot.top
        val rowHeightPx = firstDayColumn.size.height / 34f
        val targetRootY = colTop + 6 * rowHeightPx
        // 第 3 列中心 root x
        val targetRootX = firstColLeft + (3 + 0.5f) * colWidthPx

        // backlog 节点顶部/左侧在 root 中的位置（用于把 root 目标坐标转成节点本地坐标）
        val backlogNode = composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true)
            .fetchSemanticsNode()
        val backlogRootTop = backlogNode.boundsInRoot.top
        val backlogRootLeft = backlogNode.boundsInRoot.left
        val localTargetY = targetRootY - backlogRootTop
        val localTargetX = targetRootX - backlogRootLeft

        // 模拟 long-press-drag：按下 → 移动到目标列/行 → 释放（delayMillis 触发长按）
        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(Offset(localTargetX, localTargetY), delayMillis = 800L)
            up()
        }

        composeRule.waitForIdle()

        val result = captured.get()
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(1L)
        assertThat(result.second).isEqualTo(targetDate)
        // 落在第 6 行 → 09:00 = 540，时长 60 → end = 600
        assertThat(result.third).isEqualTo(540)
        assertThat(result.fourth).isEqualTo(600)
    }

    /**
     * 先横向滚动把目标列滚到可见，再拖放 → 应命中该列。
     *
     * 目标：把 2026-07-09（原屏幕外）滚到可见后，拖放到它的第 6 行，
     * 回调应携带日期 2026-07-09 + 时间段 540/600。
     */
    @Test
    fun longPressDrag_afterHorizontalScroll_snapsToScrolledColumn() {
        val captured = AtomicReference<Quad<Long, String, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                WeekSchedulePage(
                    weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
                    itemsByDate = emptyMap(),
                    backlogItems = listOf(
                        BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
                    ),
                    onScheduleSubproject = { id, date, start, end ->
                        captured.set(Quad(id, date, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        // 把目标列滚到可见（触发横向滚动）。
        val targetDate = "2026-07-09"
        composeRule.onNodeWithTag("WeekDay-$targetDate").performScrollTo()
        composeRule.waitForIdle()

        // 滚动后目标列可见，其 boundsInRoot 可靠。
        val dayColumn = composeRule.onNodeWithTag("WeekDay-$targetDate", useUnmergedTree = true)
            .fetchSemanticsNode()
        val colTop = dayColumn.boundsInRoot.top
        val rowHeightPx = dayColumn.size.height / 34f
        val targetRootY = colTop + 6 * rowHeightPx
        val targetRootX = dayColumn.boundsInRoot.left + dayColumn.size.width / 2f

        val backlogNode = composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true)
            .fetchSemanticsNode()
        val localTargetY = targetRootY - backlogNode.boundsInRoot.top
        val localTargetX = targetRootX - backlogNode.boundsInRoot.left

        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(Offset(localTargetX, localTargetY), delayMillis = 800L)
            up()
        }

        composeRule.waitForIdle()

        val result = captured.get()
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(1L)
        assertThat(result.second).isEqualTo(targetDate)
        assertThat(result.third).isEqualTo(540)
        assertThat(result.fourth).isEqualTo(600)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
