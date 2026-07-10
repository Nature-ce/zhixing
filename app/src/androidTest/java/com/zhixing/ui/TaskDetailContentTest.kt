package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): 任务详情内容区。
 *
 * 验证：
 *   - 展示子项目标题
 *   - 点击子项目 → 触发 onSubprojectClick(id) 回调
 *   - 显示状态标签
 */
class TaskDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val subprojects = listOf(
        SubprojectEntity(id = 1, taskId = 10, title = "选书目", status = "backlog"),
        SubprojectEntity(id = 2, taskId = 10, title = "划重点", status = "已完成"),
    )

    @Test
    fun shows_subproject_titles_and_status() {
        composeRule.setContent {
            ZhixingTheme {
                TaskDetailContent(
                    taskTitle = "读书笔记",
                    subprojects = subprojects,
                    onSubprojectClick = {},
                )
            }
        }

        composeRule.onNodeWithText("读书笔记").assertIsDisplayed()
        composeRule.onNodeWithText("选书目").assertIsDisplayed()
        composeRule.onNodeWithText("划重点").assertIsDisplayed()
        composeRule.onNodeWithTag("SubprojectStatus-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("SubprojectStatus-2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun click_subproject_invokes_callback_with_id() {
        var clickedId = -1L
        composeRule.setContent {
            ZhixingTheme {
                TaskDetailContent(
                    taskTitle = "读书笔记",
                    subprojects = subprojects,
                    onSubprojectClick = { clickedId = it },
                )
            }
        }

        composeRule.onNodeWithTag("SubprojectRow-1").performClick()

        assert(clickedId == 1L) { "Expected click on id=1 but was $clickedId" }
    }

    @Test
    fun abandon_button_invokes_callback_with_id() {
        var abandonedId = -1L
        composeRule.setContent {
            ZhixingTheme {
                TaskDetailContent(
                    taskTitle = "读书笔记",
                    subprojects = subprojects,
                    onSubprojectClick = {},
                    onAbandonClick = { abandonedId = it },
                )
            }
        }

        composeRule.onNodeWithTag("AbandonButton-1").performClick()

        assert(abandonedId == 1L) { "Expected abandon on id=1 but was $abandonedId" }
    }
}
