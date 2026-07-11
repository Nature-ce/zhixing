package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhixing.data.dao.ScheduleDao
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
 * Instrumented TDD (on-device): 任务列表 → 详情页导航。
 *
 * 验证：
 *   - 点击任务卡片 → 跳转到详情页，展示子项目
 *   - 详情页点击子项目 → 状态变为已完成
 */
class TaskNavHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var subprojectDao: SubprojectDao
    private lateinit var scheduleDao: ScheduleDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskDao = db.taskDao()
        subprojectDao = db.subprojectDao()
        scheduleDao = db.scheduleDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    /**
     * 任务列表非空时，列表右下应出现新增按钮（FAB "AddTaskButton"）。
     *
     * 现状：ListView 只在列表为空时暴露创建按钮，有任务卡片时无任何新增入口 → 该测试失败即 RED。
     */
    @Test
    fun addTaskButton_shown_whenListHasItems() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }

        composeRule.setContent {
            ZhixingTheme {
                TaskNavHost(
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                    scheduleDao = scheduleDao,
                )
            }
        }

        // 等任务列表加载
        runBlocking { taskDao.getAllTasks().first { it.isNotEmpty() } }

        // 新增按钮应显示
        composeRule.onNodeWithTag("AddTaskButton").assertIsDisplayed()
    }

    /**
     * 端到端创建流程（行为 #2 + #3）：
     *   列表已有任务 → 点 FAB "AddTaskButton" → 对话框出现 → 输入标题 → 确认 → 新增任务卡片出现。
     */
    @Test
    fun tappingAddButton_createsNewTask() {
        // 先放一条任务，让列表非空（FAB 才会显示）
        runBlocking { taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L)) }

        composeRule.setContent {
            ZhixingTheme {
                TaskNavHost(
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                    scheduleDao = scheduleDao,
                )
            }
        }

        // 等任务列表加载
        runBlocking { taskDao.getAllTasks().first { it.isNotEmpty() } }

        // 点 FAB → 创建对话框弹出
        composeRule.onNodeWithTag("AddTaskButton").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("TaskTitleInput").assertIsDisplayed()

        // 输入标题 → 确认
        composeRule.onNodeWithTag("TaskTitleInput").performTextInput("新任务")
        composeRule.onNodeWithText("确认").performClick()

        // 新任务卡片出现在列表（等 flow 反映新增）
        runBlocking { taskDao.getAllTasks().first { it.any { t -> t.title == "新任务" } } }
        composeRule.onNodeWithText("新任务").assertIsDisplayed()
    }

    @Test
    fun click_task_card_navigates_to_detail() {
        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "backlog", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "backlog", createdAt = 3_000L))
            id
        }

        composeRule.setContent {
            ZhixingTheme {
                TaskNavHost(
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                    scheduleDao = scheduleDao,
                )
            }
        }

        // 等任务列表加载
        runBlocking { taskDao.getAllTasks().first { it.isNotEmpty() } }

        // 点击任务卡片
        composeRule.onNodeWithTag("TaskCard-$taskId").performClick()

        // 详情页展示子项目
        composeRule.onNodeWithText("选书目").assertIsDisplayed()
        composeRule.onNodeWithText("划重点").assertIsDisplayed()
    }
}
