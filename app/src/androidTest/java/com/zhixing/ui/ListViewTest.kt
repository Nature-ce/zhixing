package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): ListView composable 渲染任务列表。
 *
 * 验证：
 *   - 空列表 → 显示 EmptyState（欢迎文案 + 创建按钮）
 *   - 有数据 → 显示任务卡片（标题 + 状态标签 + 进度数字）
 *
 * 直接传入 List<TaskListItem>（UI model），不触数据库，纯 Compose 渲染测试。
 */
class ListViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun empty_list_shows_empty_state() {
        composeRule.setContent {
            ZhixingTheme {
                ListView(taskItems = emptyList(), onCreateFirstTask = {}, hasAnyTask = false)
            }
        }

        composeRule.onNodeWithTag("EmptyStateTitle").assertIsDisplayed()
        composeRule.onNodeWithText("创建第一个任务").assertIsDisplayed()
    }

    @Test
    fun empty_list_with_completed_tasks_shows_different_message() {
        composeRule.setContent {
            ZhixingTheme {
                ListView(taskItems = emptyList(), onCreateFirstTask = {}, hasAnyTask = true)
            }
        }

        composeRule.onNodeWithText("没有进行中的任务").assertIsDisplayed()
        composeRule.onNodeWithText("去回顾页看看").assertIsDisplayed()
    }

    @Test
    fun non_empty_list_shows_task_cards() {
        val items = listOf(
            TaskListItem(
                id = 1,
                title = "读书笔记",
                statusLabel = "进行中",
                completedCount = 1,
                totalCount = 3,
            ),
            TaskListItem(
                id = 2,
                title = "写作",
                statusLabel = "已完成",
                completedCount = 2,
                totalCount = 2,
            ),
        )

        composeRule.setContent {
            ZhixingTheme {
                ListView(taskItems = items, onCreateFirstTask = {})
            }
        }

        composeRule.onNodeWithText("读书笔记").assertIsDisplayed()
        composeRule.onNodeWithText("写作").assertIsDisplayed()
        composeRule.onNodeWithText("1/3").assertIsDisplayed()
        composeRule.onNodeWithText("2/2").assertIsDisplayed()
    }

    @Test
    fun click_task_card_invokes_onTaskClick() {
        val items = listOf(
            TaskListItem(id = 1, title = "读书笔记", statusLabel = "进行中", completedCount = 1, totalCount = 3),
        )

        var clickedId = -1L
        composeRule.setContent {
            ZhixingTheme {
                ListView(
                    taskItems = items,
                    onCreateFirstTask = {},
                    onTaskClick = { clickedId = it },
                )
            }
        }

        composeRule.onNodeWithTag("TaskCard-1").performClick()

        assert(clickedId == 1L) { "Expected click on id=1 but was $clickedId" }
    }
}
