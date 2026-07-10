package com.zhixing.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.db.AppDatabase
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Instrumented TDD (on-device): ReviewViewModel 筛选已完成/已放弃任务。
 *
 * 验证：
 *   - 只返回已完成/已放弃的任务
 *   - 进行中的任务不出现
 */
class ReviewViewModelTest {

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
    fun returns_only_completed_and_abandoned_tasks() {
        runBlocking {
            // 任务 1：所有子项目终态 → 已完成
            val task1 = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = task1, title = "选书目", status = "已完成", createdAt = 2_000L))
            taskDao.updateTaskStatus(task1, "已完成")

            // 任务 2：进行中
            val task2 = taskDao.insertTask(TaskEntity(title = "写作", createdAt = 3_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = task2, title = "大纲", status = "backlog", createdAt = 4_000L))

            // 任务 3：已放弃
            val task3 = taskDao.insertTask(TaskEntity(title = "跑步", createdAt = 5_000L))
            taskDao.updateTaskStatus(task3, "已放弃")
        }

        val vm = ReviewViewModel(taskDao, subprojectDao)

        // 等稳定状态：恰好 2 个已完成/已放弃任务
        val items = runBlocking { vm.reviewItems.first { it.size >= 2 } }

        // 只有任务1（已完成）和任务3（已放弃）
        assertThat(items).hasSize(2)
        assertThat(items.map { it.title }).containsExactlyInAnyOrder("读书笔记", "跑步")
    }

    @Test
    fun picks_up_task_when_subprojects_marked_terminal_via_detail_vm() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val (taskId, firstId, secondId) = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val s1 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "backlog", createdAt = 2_000L))
            val s2 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "backlog", createdAt = 3_000L))
            Triple(id, s1, s2)
        }

        // 通过 TaskDetailViewModel 标完子项目 → 任务自动完成（改 task 表）→ 触发 getAllTasks 重发
        val detailVm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { detailVm.subprojects.first { it.size == 2 } }
        runBlocking {
            detailVm.changeSubprojectStatus(firstId, "已完成")
            detailVm.changeSubprojectStatus(secondId, "已完成")
        }
        // 等自动完成写回 task 表（轮询至 status 稳定为已完成）
        runBlocking {
            var status = ""
            while (status != "已完成") {
                status = taskDao.getTaskById(taskId)!!.status
            }
        }

        val reviewVm = ReviewViewModel(taskDao, subprojectDao)
        val items = runBlocking { reviewVm.reviewItems.first { it.isNotEmpty() } }

        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("读书笔记")
        assertThat(items[0].statusLabel).isEqualTo("已完成")
    }

    @Test
    fun shows_task_as_completed_when_all_subprojects_terminal_even_if_task_status_not_updated() {
        runBlocking {
            // 任务：所有子项目终态，但 task.status 仍是"进行中"（未触发自动完成）
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已完成", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "划重点", status = "已完成", createdAt = 3_000L))
            // 注意：不更新 task.status，模拟 TaskListViewModel 未触发的情况
        }

        val vm = ReviewViewModel(taskDao, subprojectDao)

        val items = runBlocking { vm.reviewItems.first { it.isNotEmpty() } }

        // 应出现在回顾页，状态为已完成
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("读书笔记")
        assertThat(items[0].statusLabel).isEqualTo("已完成")
    }
}
