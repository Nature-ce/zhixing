package com.zhixing.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
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
}
