package com.zhixing.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.Composable
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
 * instrumented 测试：日视图拖放排期的 UI 结构 + 拖拽视觉提示。
 *
 * 验证：
 *   - backlog 子项目渲染为 drag source（testTag "BacklogItem-${id}"）
 *   - 时间格栅渲染为 drop target（testTag "ScheduleDropTarget"）
 *   - 拖放后回调 onScheduleSubproject 被调用（时间由落点决定）
 *   - 拖拽提示（DragGlimpse）组件能独立渲染出被拖子项目的标题
 */
class DayScheduleDragDropTest {

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
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                )
            }
        }

        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("BacklogItem-2", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * 行为 #4（UI）：backlog 子项目按父任务归组，每组有独立 header 显示任务标题；
     * 药丸不再在内部印任务标题，而是作为组 header 出现。两个不同任务的子项目
     * → 两个 TaskGroupHeader，药丸归属正确。
     *
     * 当前按扁平 FlowRow 渲染，无 TaskGroupHeader → 断言失败（RED）。
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
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                )
            }
        }

        // 两个任务各自的组 header 存在
        composeRule.onNodeWithTag("TaskGroupHeader-10", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("TaskGroupHeader-20", useUnmergedTree = true).assertIsDisplayed()
        // 任务标题作为组头文本出现（不在药丸内）
        composeRule.onNodeWithText("读书笔记", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("筹备婚礼", useUnmergedTree = true).assertIsDisplayed()
        // 所有药丸仍渲染
        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("BacklogItem-2", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("BacklogItem-3", useUnmergedTree = true).assertIsDisplayed()
    }

    /**
     * 行为 #5（UI）：点击组 header 折叠/展开该组的子项目药丸。
     *
     * 折叠"读书笔记"组 → 其药丸（选书目/划重点）隐藏，"筹备婚礼"组药丸仍可见；
     * 再点一次 → 恢复可见。
     */
    @Test
    fun collapsing_taskGroup_hidesItsPills() {
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目", taskId = 10L, taskTitle = "读书笔记"),
            BacklogItem(id = 2, title = "挑礼物", taskId = 20L, taskTitle = "筹备婚礼"),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                )
            }
        }

        // 初始展开：两组药丸都可见
        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("挑礼物", useUnmergedTree = true).assertIsDisplayed()

        // 折叠"读书笔记"组
        composeRule.onNodeWithTag("TaskGroupHeader-10", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("选书目", useUnmergedTree = true).assertCountEquals(0)
        // 另一组不受影响
        composeRule.onNodeWithText("挑礼物", useUnmergedTree = true).assertIsDisplayed()
        // 折叠态组头箭头旋转为"展开"方向并保留，让可折叠性一目了然
        composeRule.onAllNodesWithContentDescription("展开", useUnmergedTree = true).assertCountEquals(1)

        // 再次点击 → 展开恢复
        composeRule.onNodeWithTag("TaskGroupHeader-10", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun grid_renders_as_drop_target() {
        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = listOf(BacklogItem(id = 1, title = "选书目")),
                )
            }
        }

        composeRule.onNodeWithTag("ScheduleDropTarget", useUnmergedTree = true).assertExists()
    }

    /**
     * 短延迟（300ms）长按即可激活拖拽排期。
     *
     * 300ms 短于系统默认长按超时（~500ms），若实现沿用默认超时则长按不会触发 → 拖拽无法激活 → 该测试失败。
     * 把长按超时降到 200ms 后，300ms 足以触发 → 测试通过。
     */
    @Test
    fun shortDelayLongPressDrag_schedulesAtDropRow() {
        val captured = AtomicReference<Triple<Long, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = listOf(
                        BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
                    ),
                    onScheduleSubproject = { id, start, end ->
                        captured.set(Triple(id, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        val gridNode = composeRule.onNodeWithTag("ScheduleDropTarget", useUnmergedTree = true)
            .fetchSemanticsNode()
        val gridTop = gridNode.boundsInRoot.top
        val rowHeightPx = gridNode.size.height / 34f
        val targetRootY = gridTop + 6 * rowHeightPx

        val backlogNode = composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true)
            .fetchSemanticsNode()
        val backlogRootTop = backlogNode.boundsInRoot.top
        val localTargetY = targetRootY - backlogRootTop

        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(Offset(center.x, localTargetY), delayMillis = 300L)
            up()
        }

        composeRule.waitForIdle()

        val result = captured.get()
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(1L)
        // 落在第 6 行 → 09:00 = 540，时长 60 → end = 600
        assertThat(result.second).isEqualTo(540)
        assertThat(result.third).isEqualTo(600)
    }

    /**
     * 展开态 header：有"待排期"标题 + "收起"向下箭头，无计数徽标（badge）。
     *
     * 行为 #1：Badge（未排期个数）已被移除，无论展开还是折叠都不应出现计数数字。
     */
    @Test
    fun backlog_header_expandedShowsChevronWithoutBadge() {
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目"),
            BacklogItem(id = 2, title = "划重点"),
        )

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                )
            }
        }

        // 标题 + 向下箭头（收起）存在
        composeRule.onNodeWithText("待排期", useUnmergedTree = true).assertIsDisplayed()
        // 整体 backlog header + 各组 header 均可能带"收起"chevron，断言至少一个可见
        composeRule.onAllNodesWithContentDescription("收起", useUnmergedTree = true).onFirst().assertIsDisplayed()
        // 计数徽标 "2" 不应存在
        composeRule.onAllNodesWithText("2", useUnmergedTree = true).assertCountEquals(0)
    }

    /**
     * 折叠状态由页面外部持有（首页 MainScreen 层级），通过 collapsed/onCollapsedChange 传入。
     *
     * 验证：
     *   - collapsed=false（默认）时药丸可见
     *   - 页面本身不再持有 collapse 状态，点击 header 调用 onCollapsedChange(!collapsed)
     *   - 外部 state 翻转后页面跟着折叠（prop 驱动 recomposition），药丸隐藏、header 仍在
     *
     * 与原内部状态版的区别：页面不再自己翻转 collapsed，纯粹由 prop 驱动——
     * 这样 state 提到 MainScreen 后，切 tab 再切回来不会丢失（与 isScheduleWeekView 同级）。
     */
    @Test
    fun backlog_canBeCollapsed_andRestored() {
        val backlog = listOf(
            BacklogItem(id = 1, title = "选书目"),
            BacklogItem(id = 2, title = "划重点"),
        )
        composeRule.setContent {
            ZhixingTheme {
                // 外部持有的折叠状态（模拟 MainScreen 层级的 collapsedBacklog）。
                var externalCollapsed by remember { mutableStateOf(false) }
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = backlog,
                    collapsed = externalCollapsed,
                    onCollapsedChange = { externalCollapsed = it },
                )
            }
        }

        // 初始展开：药丸可见
        composeRule.onNodeWithText("选书目", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("划重点", useUnmergedTree = true).assertIsDisplayed()

        // 点 header → 调用 onCollapsedChange(true)，外部状态翻转 → recomposition → 折叠
        composeRule.onNodeWithTag("BacklogHeader", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("选书目", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("划重点", useUnmergedTree = true).assertCountEquals(0)

        // header 仍在；箭头始终可见并通过旋转表达方向（折叠态朝右 = "展开"），
        // 不再"折叠态无可发现性指示符"，让可折叠性一目了然。
        composeRule.onNodeWithTag("BacklogHeader", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("展开", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun longPressDrag_fromBacklogIntoGrid_schedulesAtDropRow() {
        // 捕获回调参数
        val captured = AtomicReference<Triple<Long, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = listOf(
                        BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
                    ),
                    onScheduleSubproject = { id, start, end ->
                        captured.set(Triple(id, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        // 格栅第 6 行 = 09:00 = 540。计算目标在 root 中的 y。
        val gridNode = composeRule.onNodeWithTag("ScheduleDropTarget", useUnmergedTree = true)
            .fetchSemanticsNode()
        val gridTop = gridNode.boundsInRoot.top
        // 第 6 行顶部 y = 6 * (行高)，行高 = 格栅总像素高 / 34 行
        val rowHeightPx = gridNode.size.height / 34f
        val targetRootY = gridTop + 6 * rowHeightPx

        // backlog 节点顶部在 root 中的 y（用于把 root 目标 y 转成节点本地坐标）
        val backlogNode = composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true)
            .fetchSemanticsNode()
        val backlogRootTop = backlogNode.boundsInRoot.top
        // performTouchInput 内坐标是节点本地坐标；格栅在 backlog 上方，故 localY 为负
        val localTargetY = targetRootY - backlogRootTop

        // 模拟 long-press-drag：按下 → 等长按触发 → 移动到格栅第 6 行 → 释放
        // 全部在一个指针输入会话内完成；moveTo 的 delayMillis 在移动前推进时钟，
        // 从而触发长按超时（autoAdvance 保持 true，让框架逐帧恢复手势协程）。
        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).performTouchInput {
            down(center)
            // 移动到目标行（本地坐标，中心 x，负 y），delayMillis 触发长按
            moveTo(Offset(center.x, localTargetY), delayMillis = 800L)
            up()
        }

        composeRule.waitForIdle()

        val result = captured.get()
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(1L)
        // 落在第 6 行 → 09:00 = 540，时长 60 → end = 600
        assertThat(result.second).isEqualTo(540)
        assertThat(result.third).isEqualTo(600)
    }

    /**
     * 已排期块长按拖拽到格栅新时段 → onRescheduleSubproject 被调用，
     * 新 startTime/endTime 由落点决定，时长保持不变（核心路径）。
     *
     * 块 09:00-10:00（时长 60）拖到第 10 行（11:00=660）→ 新时段 11:00-12:00（660-720）。
     */
    @Test
    fun longPressDrag_blockToNewTimeSlot_reschedulesPreservingDuration() {
        val captured = AtomicReference<Triple<Long, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = listOf(
                        ScheduleItem(
                            id = 100,
                            subprojectId = 10,
                            subprojectTitle = "写论文",
                            startTime = 540,   // 09:00
                            endTime = 600,     // 10:00, 时长 60
                        ),
                    ),
                    backlogItems = emptyList(),
                    onRescheduleSubproject = { id, start, end ->
                        captured.set(Triple(id, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        // 格栅第 10 行 = 11:00 = 660。计算目标在 root 中的 y。
        val gridNode = composeRule.onNodeWithTag("ScheduleDropTarget", useUnmergedTree = true)
            .fetchSemanticsNode()
        val gridTop = gridNode.boundsInRoot.top
        val rowHeightPx = gridNode.size.height / 34f
        val targetRootY = gridTop + 10 * rowHeightPx

        // 块节点顶部在 root 中的 y（用于把 root 目标 y 转成块本地坐标）
        val blockNode = composeRule.onNodeWithTag("ScheduleBlock-100", useUnmergedTree = true)
            .fetchSemanticsNode()
        val blockRootTop = blockNode.boundsInRoot.top
        val localTargetY = targetRootY - blockRootTop

        composeRule.onNodeWithTag("ScheduleBlock-100", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(Offset(center.x, localTargetY), delayMillis = 800L)
            up()
        }

        composeRule.waitForIdle()

        val result = captured.get()
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(10L)
        // 落在第 10 行 → 11:00 = 660，时长 60 → end = 720
        assertThat(result.second).isEqualTo(660)
        assertThat(result.third).isEqualTo(720)
    }

    /**
     * 已排项块拖到格栅外（向上越过格栅顶部 → yPx < 0）→ 不触发 onRescheduleSubproject，
     * 块保持原位（越界 = 无变化）。
     */
    @Test
    fun dragBlock_outsideGrid_doesNotReschedule() {
        val captured = AtomicReference<Triple<Long, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = listOf(
                        ScheduleItem(
                            id = 100,
                            subprojectId = 10,
                            subprojectTitle = "写论文",
                            startTime = 540,   // 09:00
                            endTime = 600,     // 10:00
                        ),
                    ),
                    backlogItems = emptyList(),
                    onRescheduleSubproject = { id, start, end ->
                        captured.set(Triple(id, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        // 目标：块本地坐标中一个很大的负 y（手指移到块顶部很远的上方 → 越过格栅顶部 → yPx < 0）。
        val localTargetY = -2000f

        composeRule.onNodeWithTag("ScheduleBlock-100", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(Offset(center.x, localTargetY), delayMillis = 800L)
            up()
        }

        composeRule.waitForIdle()

        // 越界释放 → 不触发重排
        assertThat(captured.get()).isNull()
    }

    @Test
    fun dragBackToBacklog_cancelsSchedule() {
        // 拖向格栅后再拖回 backlog 区域释放 → 不应排期。
        val captured = AtomicReference<Triple<Long, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    backlogItems = listOf(
                        BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
                    ),
                    onScheduleSubproject = { id, start, end ->
                        captured.set(Triple(id, start, end))
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

    /**
     * 拖拽提示（DragGlimpse）作为独立 UI 单元的测试。
     *
     * 因为"进行中"是瞬时态，instrumented 测试难以稳定捕获——performTouchInput
     * 块结束时若未调用 up()，框架会取消指针会话导致 dragState 归零。
     * 故把 glimpse 抽成 internal 组件，单独测试其"给定标题就能渲染"这一行为。
     */
    @Test
    fun dragGlimpse_rendersDraggedItemTitle() {
        composeRule.setContent {
            ZhixingTheme {
                Box(modifier = Modifier.size(400.dp).testTag("GlimpseHost")) {
                    DragGlimpse(
                        title = "选书目",
                        offsetX = 120f,
                        offsetY = 240f,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("DragGlimpse", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("选书目")
    }

    /**
     * 在 inline 面板里把预期时间从 60 改成 90，关闭面板后拖拽药丸到格栅排期，
     * 结束时间应反映面板里新编辑的 90min（09:00 + 90 = 10:30 = 630），
     * 而非沿用 backlog prop 的旧值 60 算出的 600。
     *
     * 关键时序：面板关闭后 overlay 已销毁，拖拽路径必须仍能拿到"最新编辑值"。
     */
    @Test
    fun dragAfterPanelEdit_usesEditedDurationNotInProp() {
        val captured = AtomicReference<Triple<Long, Int, Int>?>(null)

        composeRule.setContent {
            ZhixingTheme {
                DaySchedulePage(
                    date = "2026-07-09",
                    scheduleItems = emptyList(),
                    // prop 中 estimatedDuration 保持 60（模拟 VM 异步刷新尚未完成），
                    // 面板里改成 90 后拖拽应即时使用 90。
                    backlogItems = listOf(
                        BacklogItem(id = 1, title = "选书目", estimatedDuration = 60),
                    ),
                    onScheduleSubproject = { id, start, end ->
                        captured.set(Triple(id, start, end))
                    },
                )
            }
        }

        composeRule.waitForIdle()

        // 1. 点药丸 → 展开 inline 面板 → 把预期时间 60 改成 90
        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanelDuration-1")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("BacklogPanelDuration-1", useUnmergedTree = true)
            .performTextReplacement("90")
        composeRule.waitForIdle()

        // 2. 关闭面板（点「取消」），overlay 随之销毁
        composeRule.onNodeWithTag("BacklogPanelCancel-1", useUnmergedTree = true).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("BacklogPanel-1").fetchSemanticsNodes().isEmpty()
        }

        // 3. 长按拖拽药丸到格栅第 6 行（09:00 = 540）
        val gridNode = composeRule.onNodeWithTag("ScheduleDropTarget", useUnmergedTree = true)
            .fetchSemanticsNode()
        val gridTop = gridNode.boundsInRoot.top
        val rowHeightPx = gridNode.size.height / 34f
        val targetRootY = gridTop + 6 * rowHeightPx

        val backlogNode = composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true)
            .fetchSemanticsNode()
        val backlogRootTop = backlogNode.boundsInRoot.top
        val localTargetY = targetRootY - backlogRootTop

        composeRule.onNodeWithTag("BacklogItem-1", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(Offset(center.x, localTargetY), delayMillis = 800L)
            up()
        }

        composeRule.waitForIdle()

        val result = captured.get()
        assertThat(result).isNotNull
        assertThat(result!!.first).isEqualTo(1L)
        // 落在第 6 行 → 09:00 = 540；面板编辑后时长 90 → end = 630（而非 prop 旧值 600）
        assertThat(result.second).isEqualTo(540)
        assertThat(result.third).isEqualTo(630)
    }
}
