package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.ui.theme.ZhixingTheme
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): ReviewDetailPage 回顾详情页。
 *
 * 验证：
 *   - 展示任务标题 + 子项目列表
 *   - 展示回顾文本输入框
 *   - 输入回顾文本后保存 → 回调触发
 */
class ReviewDetailPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val subprojects = listOf(
        SubprojectEntity(id = 1, taskId = 10, title = "选书目", status = "已完成"),
        SubprojectEntity(id = 2, taskId = 10, title = "划重点", status = "已完成"),
    )

    @Test
    fun shows_task_title_and_subprojects() {
        composeRule.setContent {
            ZhixingTheme {
                ReviewDetailPage(
                    taskTitle = "读书笔记",
                    subprojects = subprojects,
                    reviewText = "",
                    onReviewChange = {},
                )
            }
        }

        assert(composeRule.onAllNodesWithText("读书笔记", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("选书目", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("划重点", substring = true).assertIsDisplayed()
    }

    @Test
    fun input_review_text_invokes_onReviewChange() {
        var reviewedText = ""
        composeRule.setContent {
            ZhixingTheme {
                ReviewDetailPage(
                    taskTitle = "读书笔记",
                    subprojects = subprojects,
                    reviewText = "",
                    onReviewChange = { reviewedText = it },
                )
            }
        }

        composeRule.onNodeWithTag("ReviewTextInput").performTextInput("收获很大")

        assert(reviewedText == "收获很大") { "Expected '收获很大' but was '$reviewedText'" }
    }
}
