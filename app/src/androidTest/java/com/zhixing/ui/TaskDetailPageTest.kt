package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Instrumented TDD (on-device): TaskDetailPage 集成。
 *
 * 验证：
 *   - 展示任务标题 + 子项目列表
 *   - 点击子项目 → 状态变为"已完成"并持久化
 */
class TaskDetailPageTest {

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
    fun shows_task_title_and_subprojects() {
        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "backlog", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "backlog", createdAt = 3_000L))
            id
        }

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = taskId,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                )
            }
        }

        // "读书笔记"在 TopAppBar 和 Content 各显示一次，用 onAllNodesWithText
        composeRule.onAllNodesWithText("读书笔记")[0].assertIsDisplayed()
        composeRule.onNodeWithText("选书目").assertIsDisplayed()
        composeRule.onNodeWithText("划重点").assertIsDisplayed()
    }

    @Test
    fun click_subproject_changes_status_to_completed() {
        val subId = runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L))
        }

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = 1L,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                )
            }
        }

        // 等子项目加载完
        runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first { it.isNotEmpty() } }

        composeRule.onNodeWithTag("SubprojectRow-$subId").performClick()

        // 等 flow 反映更新后再读 DB
        runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first { it.any { s -> s.status == "已完成" } } }

        val updated = runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first() }
        assertThat(updated[0].status).isEqualTo("已完成")
    }

    @Test
    fun add_subproject_appears_in_list() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = taskId,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                )
            }
        }

        // 点击"添加子项目"按钮
        composeRule.onNodeWithTag("AddSubprojectButton").performClick()

        // 输入子项目标题
        composeRule.onNodeWithTag("SubprojectTitleInput").performTextInput("选书目")

        // 确认
        composeRule.onNodeWithText("确认").performClick()

        // 等 flow 反映新子项目
        runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first { it.isNotEmpty() } }

        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).hasSize(1)
        assertThat(subs[0].title).isEqualTo("选书目")
        assertThat(subs[0].status).isEqualTo("backlog")
    }

    @Test
    fun schedule_subproject_shows_dialog_and_persists() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }
        val subId = runBlocking {
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L))
        }

        val scheduleDao: ScheduleDao = db.scheduleDao()

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = taskId,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                    scheduleDao = scheduleDao,
                )
            }
        }

        // 等子项目加载
        runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first { it.isNotEmpty() } }

        // 点击排期按钮 → 弹出排期对话框（默认 09:00-10:00 合法）
        composeRule.onNodeWithTag("ScheduleButton-$subId").performClick()

        // 直接确认（不拨动滚轮，使用默认时间段）
        composeRule.onNodeWithText("确认").performClick()

        // 等 flow 反映状态变化
        runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first { it.any { s -> s.status == "已排期" } } }

        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        assertThat(subs[0].status).isEqualTo("已排期")
    }

    @Test
    fun click_abandon_button_changes_subproject_status_to_abandoned() {
        val subId = runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L))
        }

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = 1L,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                )
            }
        }

        runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first { it.isNotEmpty() } }

        composeRule.onNodeWithTag("AbandonButton-$subId").performClick()

        runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first { it.any { s -> s.status == "已放弃" } } }

        val updated = runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first() }
        assertThat(updated[0].status).isEqualTo("已放弃")
    }

    @Test
    fun abandon_task_shows_confirm_dialog_and_invokes_onBack() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }

        var backCalled = false

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = taskId,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                    onBack = { backCalled = true },
                )
            }
        }

        // 点右上角菜单
        composeRule.onNodeWithTag("TaskMenuButton").performClick()
        // 点"放弃任务"
        composeRule.onNodeWithTag("AbandonTaskMenuItem").performClick()
        // 确认弹窗出现 → 点确认
        composeRule.onNodeWithText("确认").performClick()

        // 等 VM 执行完，验证 onBack 被触发
        Thread.sleep(500)
        assertThat(backCalled).isTrue()
    }

    @Test
    fun delete_task_shows_confirm_dialog_and_invokes_onBack() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }

        var backCalled = false

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = taskId,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                    onBack = { backCalled = true },
                )
            }
        }

        composeRule.onNodeWithTag("TaskMenuButton").performClick()
        composeRule.onNodeWithTag("DeleteTaskMenuItem").performClick()
        composeRule.onNodeWithText("确认").performClick()

        Thread.sleep(500)
        assertThat(backCalled).isTrue()
    }

    @Test
    fun cancel_abandon_dialog_does_not_invoke_onBack() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }

        var backCalled = false

        composeRule.setContent {
            ZhixingTheme {
                TaskDetailPage(
                    taskId = taskId,
                    taskTitle = "读书笔记",
                    taskDao = taskDao,
                    subprojectDao = subprojectDao,
                    onBack = { backCalled = true },
                )
            }
        }

        composeRule.onNodeWithTag("TaskMenuButton").performClick()
        composeRule.onNodeWithTag("AbandonTaskMenuItem").performClick()
        // 点取消
        composeRule.onNodeWithText("取消").performClick()

        Thread.sleep(300)
        assertThat(backCalled).isFalse()
    }
}
