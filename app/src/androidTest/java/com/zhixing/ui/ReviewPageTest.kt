package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): ReviewPage 回顾列表页。
 *
 * 验证：
 *   - 展示已完成/已放弃的任务
 *   - 无已完成任务时显示空状态
 */
class ReviewPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun shows_completed_tasks() {
        val items = listOf(
            TaskListItem(id = 1, title = "读书笔记", statusLabel = "已完成", completedCount = 3, totalCount = 3),
            TaskListItem(id = 2, title = "写作", statusLabel = "已放弃", completedCount = 0, totalCount = 2),
        )

        composeRule.setContent {
            ZhixingTheme {
                ReviewPage(taskItems = items)
            }
        }

        composeRule.onNodeWithText("读书笔记").assertIsDisplayed()
        composeRule.onNodeWithText("写作").assertIsDisplayed()
    }

    @Test
    fun empty_review_shows_empty_state() {
        composeRule.setContent {
            ZhixingTheme {
                ReviewPage(taskItems = emptyList())
            }
        }

        composeRule.onNodeWithTag("ReviewEmptyState").assertIsDisplayed()
    }

    @Test
    fun shows_subproject_entries_under_task_title() {
        val items = listOf(
            TaskListItem(
                id = 1,
                title = "读书笔记",
                statusLabel = "已完成",
                completedCount = 2,
                totalCount = 2,
                subprojectEntries = listOf(
                    SubprojectEntry(id = 1, title = "选书目", status = "已完成"),
                    SubprojectEntry(id = 2, title = "划重点", status = "已放弃"),
                ),
            ),
        )

        composeRule.setContent {
            ZhixingTheme {
                ReviewPage(taskItems = items)
            }
        }

        composeRule.onNodeWithText("读书笔记").assertIsDisplayed()
        composeRule.onNodeWithText("选书目", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("划重点", substring = true).assertIsDisplayed()
    }
}
