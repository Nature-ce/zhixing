package com.zhixing.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * Instrumented TDD (on-device): TaskListViewModel 列表组装。
 *
 * VM 继承 androidx.lifecycle.ViewModel，用 viewModelScope 管理协程；
 * 测试用 runBlocking 等首次 emit 后断言。
 */
class TaskListViewModelTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun view_model_emits_task_list_items_with_correct_progress() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()

        val vm = runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已完成", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "划重点", status = "backlog", createdAt = 3_000L))

            TaskListViewModel(taskDao, subprojectDao)
        }

        runBlocking {
            val items = vm.taskItems.first { it.isNotEmpty() }
            assertThat(items).hasSize(1)

            val item = items[0]
            assertThat(item.title).isEqualTo("读书笔记")
            assertThat(item.statusLabel).isEqualTo("进行中")
            assertThat(item.completedCount).isEqualTo(1)
            assertThat(item.totalCount).isEqualTo(2)
        }
    }

    @Test
    fun all_subprojects_terminal_auto_completes_task_status() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()

        val vm = runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "全完成任务", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "A", status = "已完成", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "B", status = "已放弃", createdAt = 3_000L))
            TaskListViewModel(taskDao, subprojectDao)
        }

        runBlocking {
            val items = vm.taskItems.first { it.isNotEmpty() }
            assertThat(items[0].statusLabel).isEqualTo("已完成")

            val task = taskDao.getTaskById(1L)
            assertThat(task?.status).isEqualTo("已完成")
        }
    }

    @Test
    fun completed_and_abandoned_tasks_do_not_appear_in_task_list() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()

        runBlocking {
            // 任务 1：进行中
            val t1 = taskDao.insertTask(TaskEntity(title = "进行中任务", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = t1, title = "A", status = "backlog", createdAt = 2_000L))

            // 任务 2：已完成
            val t2 = taskDao.insertTask(TaskEntity(title = "已完成任务", status = "已完成", createdAt = 3_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = t2, title = "B", status = "已完成", createdAt = 4_000L))

            // 任务 3：已放弃
            val t3 = taskDao.insertTask(TaskEntity(title = "已放弃任务", status = "已放弃", createdAt = 5_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = t3, title = "C", status = "已放弃", createdAt = 6_000L))
        }

        val vm = TaskListViewModel(taskDao, subprojectDao)

        val items = runBlocking { vm.taskItems.first { it.isNotEmpty() } }

        // 只有"进行中任务"出现在任务列表
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("进行中任务")
    }

    @Test
    fun abandoned_task_status_is_not_overwritten_by_auto_complete() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()

        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "已完成", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "已排期", createdAt = 3_000L))
            id
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, db.scheduleDao())
        runBlocking { vm.subprojects.first { it.isNotEmpty() } }

        // 直接调 abandonTask（suspend），确认 task.status 正确为"已放弃"而非被覆盖为"已完成"
        runBlocking { vm.abandonTask() }

        // 让 TaskListViewModel 也跑一遍，模拟真实并发场景。
        // 注意：已放弃是终态，任务不进 taskItems（被过滤），
        // 所以这里用 hasAnyTask 等待任务被观察到，而非 taskItems.first{isNotEmpty}。
        val listVm = TaskListViewModel(taskDao, subprojectDao)
        runBlocking { listVm.hasAnyTask.first { it } }

        val task = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(task!!.status).isEqualTo("已放弃")
    }

    @Test
    fun hasAnyTask_is_false_when_no_tasks_true_when_has_tasks() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()

        val vm = TaskListViewModel(taskDao, subprojectDao)
        runBlocking { /* 等首次 emit */ Thread.sleep(100) }

        // 无任务
        assertThat(vm.hasAnyTask.value).isFalse()

        // 插入任务
        runBlocking { taskDao.insertTask(TaskEntity(title = "A", createdAt = 1_000L)) }
        runBlocking { vm.hasAnyTask.first { it } }

        assertThat(vm.hasAnyTask.value).isTrue()
    }

    @Test
    fun add_task_inserts_task_and_emits_in_list() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()

        val vm = TaskListViewModel(taskDao, subprojectDao)

        runBlocking {
            vm.addTask("新项目")

            val items = vm.taskItems.first { it.isNotEmpty() }
            assertThat(items).hasSize(1)
            assertThat(items[0].title).isEqualTo("新项目")
            assertThat(items[0].statusLabel).isEqualTo("进行中")
            assertThat(items[0].totalCount).isEqualTo(0)
        }
    }
}
