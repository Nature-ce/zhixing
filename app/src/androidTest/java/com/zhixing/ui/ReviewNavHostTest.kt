package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.db.AppDatabase
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity
import com.zhixing.ui.theme.ZhixingTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * TDD (RED->GREEN): 回顾 tab 导航 —— 列表 → 详情。
 *
 * 验证：
 *   - 点击已完成任务卡片 → 跳转回顾详情页，展示标题 + 子项目
 */
class ReviewNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var subprojectDao: SubprojectDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskDao = db.taskDao()
        subprojectDao = db.subprojectDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun click_review_card_navigates_to_detail() {
        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "已完成", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "已完成", createdAt = 3_000L))
            id
        }

        composeRule.setContent {
            ZhixingTheme {
                ReviewNavHost(
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                )
            }
        }

        // 等回顾列表加载（任务所有子项目终态才出现）
        runBlocking { taskDao.getAllTasks().first { it.isNotEmpty() } }

        // 点击回顾卡片
        composeRule.onNodeWithTag("ReviewTaskCard-$taskId").performClick()

        // 详情页展示标题
        composeRule.onAllNodesWithText("读书笔记")[0].assertIsDisplayed()
        // 展示子项目（格式 "选书目 - 已完成"）
        composeRule.onNodeWithText("选书目", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("划重点", substring = true).assertIsDisplayed()
    }
}
