package com.zhixing.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhixing.data.DecompositionException
import com.zhixing.data.DecompositionResult
import com.zhixing.data.DecompositionService
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.db.AppDatabase
import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Instrumented TDD (on-device): TaskDetailViewModel 子项目加载 + 状态流转。
 *
 * 验证：
 *   - 按 taskId 加载子项目列表
 *   - changeSubprojectStatus(id, to) 调用 SubprojectState.transition 并持久化
 *   - 非法流转抛异常（由 SubprojectState 领域逻辑保证）
 */
class TaskDetailViewModelTest {

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
    fun loads_subprojects_for_task() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "backlog", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "backlog", createdAt = 3_000L))
            id
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)

        val subs = runBlocking { vm.subprojects.first { it.isNotEmpty() } }
        assertThat(subs).hasSize(2)
        assertThat(subs.map { it.title }).containsExactly("选书目", "划重点")
    }

    @Test
    fun change_subproject_status_persists_transition() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()

        val subId = runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L))
        }

        val scheduleDao: ScheduleDao = db.scheduleDao()
        val vm = TaskDetailViewModel(1L, taskDao, subprojectDao, scheduleDao)

        // 等 VM 加载完子项目，否则 changeSubprojectStatus 找不到目标
        runBlocking { vm.subprojects.first { it.isNotEmpty() } }

        runBlocking {
            vm.changeSubprojectStatus(subId, "已完成")
        }
        // 等 flow 反映更新后再读 DB，确认持久化
        runBlocking { vm.subprojects.first { it.any { s -> s.status == "已完成" } } }

        val updated = runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first() }
        assertThat(updated[0].status).isEqualTo("已完成")
    }

    @Test
    fun task_auto_completes_when_all_subprojects_are_terminal() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val (taskId, firstId, secondId) = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val s1 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "backlog", createdAt = 2_000L))
            val s2 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "backlog", createdAt = 3_000L))
            Triple(id, s1, s2)
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { vm.subprojects.first { it.size == 2 } }

        // 标完第一个 → 任务仍为"进行中"
        runBlocking { vm.changeSubprojectStatus(firstId, "已完成") }
        runBlocking { vm.subprojects.first { it.any { s -> s.id == firstId && s.status == "已完成" } } }

        val taskAfterFirst = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(taskAfterFirst!!.status).isEqualTo("进行中")

        // 标完第二个 → 任务自动变"已完成"
        runBlocking { vm.changeSubprojectStatus(secondId, "已完成") }
        runBlocking { vm.subprojects.first { it.all { s -> s.status == "已完成" } } }

        val taskAfterSecond = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(taskAfterSecond!!.status).isEqualTo("已完成")
    }

    @Test
    fun abandon_subproject_then_all_terminal_task_auto_completes() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val (taskId, firstId, secondId) = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val s1 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "backlog", createdAt = 2_000L))
            val s2 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "已排期", createdAt = 3_000L))
            Triple(id, s1, s2)
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { vm.subprojects.first { it.size == 2 } }

        // 第一个子项目放弃，第二个保持已排期 → 任务仍为进行中
        runBlocking { vm.changeSubprojectStatus(firstId, "已放弃") }
        runBlocking { vm.subprojects.first { it.any { s -> s.id == firstId && s.status == "已放弃" } } }

        val taskAfterFirst = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(taskAfterFirst!!.status).isEqualTo("进行中")

        // 第二个也放弃 → 全部终态 → 任务自动完成
        runBlocking { vm.changeSubprojectStatus(secondId, "已放弃") }
        runBlocking { vm.subprojects.first { it.all { s -> s.status == "已放弃" } } }

        val taskAfterSecond = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(taskAfterSecond!!.status).isEqualTo("已完成")
    }

    @Test
    fun schedule_subproject_inserts_schedule_and_updates_status() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val subId = runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L))
        }

        val vm = TaskDetailViewModel(1L, taskDao, subprojectDao, scheduleDao)

        // 等 VM 加载完子项目，否则 scheduleSubproject 找不到目标
        runBlocking { vm.subprojects.first { it.isNotEmpty() } }

        runBlocking {
            vm.scheduleSubproject(subId, "2026-07-08", 600, 660)
        }

        // 等 flow 反映状态变化
        runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first { it.any { s -> s.status == "已排期" } } }

        val updatedSub = runBlocking { subprojectDao.getSubprojectsByTaskId(1L).first() }
        assertThat(updatedSub[0].status).isEqualTo("已排期")

        val schedules = runBlocking { scheduleDao.getScheduleItemsByDate("2026-07-08").first() }
        assertThat(schedules).hasSize(1)
        assertThat(schedules[0].subprojectId).isEqualTo(subId)
        assertThat(schedules[0].startTime).isEqualTo(600)
        assertThat(schedules[0].endTime).isEqualTo(660)
    }

    @Test
    fun abandon_task_sets_all_subprojects_to_abandoned_and_clears_schedules() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val s1 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "已完成", createdAt = 2_000L))
            val s2 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "已排期", createdAt = 3_000L))
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = s2, date = "2026-07-08", startTime = 600, endTime = 660))
            id
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { vm.subprojects.first { it.isNotEmpty() } }

        runBlocking { vm.abandonTask() }

        // 等 flow 反映
        runBlocking { vm.subprojects.first { it.all { s -> s.status == "已放弃" } } }

        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).allMatch { it.status == "已放弃" }

        val task = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(task!!.status).isEqualTo("已放弃")

        // 日程已清空（子项目已放弃 → schedule 已清）
        val schedules = runBlocking { scheduleDao.getScheduleItemsByDate("2026-07-08").first() }
        assertThat(schedules).isEmpty()
    }

    @Test
    fun abandon_task_writes_task_status_before_returning() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "已完成", createdAt = 2_000L))
            id
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { vm.subprojects.first { it.isNotEmpty() } }

        // abandonTask 是 suspend，完成后所有 DB 写入已提交
        runBlocking { vm.abandonTask() }

        // 立即读 DB，task.status 必须是"已放弃"（不依赖 flow 延迟）
        val task = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(task!!.status).isEqualTo("已放弃")

        // 验证该任务会出现在回顾页（ReviewViewModel 筛选）
        val reviewVm = ReviewViewModel(taskDao, subprojectDao)
        val items = runBlocking { reviewVm.reviewItems.first { it.isNotEmpty() } }
        assertThat(items).hasSize(1)
        assertThat(items[0].title).isEqualTo("读书笔记")
    }

    @Test
    fun delete_task_cascades_to_subprojects_and_schedules() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val s1 = subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "已完成", createdAt = 2_000L))
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = s1, date = "2026-07-08", startTime = 600, endTime = 660))
            id
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { vm.subprojects.first { it.isNotEmpty() } }

        runBlocking { vm.deleteTask() }

        val task = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(task).isNull()

        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).isEmpty()

        val schedules = runBlocking { scheduleDao.getScheduleItemsByDate("2026-07-08").first() }
        assertThat(schedules).isEmpty()
    }

    /** 纯内存假服务，用于 instrumented 验证 decompose 编排（不触及网络）。 */
    private class FakeDecompositionService(
        private val results: List<DecompositionResult> = emptyList(),
        private val error: String? = null,
    ) : DecompositionService {
        override suspend fun decompose(taskTitle: String, taskDescription: String?): List<DecompositionResult> {
            if (error != null) throw DecompositionException(error)
            return results
        }
    }

    @Test
    fun decompose_inserts_subprojects_from_service() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "写报告", createdAt = 1_000L))
        }
        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { vm.subprojects.first { it.isEmpty() } }

        val service = FakeDecompositionService(
            results = listOf(
                DecompositionResult("收集资料", 30),
                DecompositionResult("撰写大纲", 45),
            ),
        )
        runBlocking { vm.decompose(service) }

        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).hasSize(2)
        assertThat(subs.map { it.title }).containsExactly("收集资料", "撰写大纲")
        assertThat(subs.map { it.estimatedDuration }).containsExactly(30, 45)
        assertThat(subs).allMatch { it.status == "backlog" }
        assertThat(subs).allMatch { it.taskId == taskId }
    }

    @Test
    fun decompose_withReplaceExisting_clearsOld_subprojects() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "写报告", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "旧步骤", status = "backlog", createdAt = 2_000L))
            id
        }
        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)
        runBlocking { vm.subprojects.first { it.isNotEmpty() } }

        val service = FakeDecompositionService(
            results = listOf(DecompositionResult("新步骤", 20)),
        )
        runBlocking { vm.decompose(service, replaceExisting = true) }

        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).hasSize(1)
        assertThat(subs[0].title).isEqualTo("新步骤")
    }

    @Test
    fun decompose_propagates_service_error() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "写报告", createdAt = 1_000L))
        }
        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)

        val service = FakeDecompositionService(error = "网络连接失败")
        assertThatThrownBy { runBlocking { vm.decompose(service) } }
            .isInstanceOf(DecompositionException::class.java)
            .hasMessageContaining("网络")

        // 出错时不应写入任何子项目
        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).isEmpty()
    }

    @Test
    fun update_task_info_persists_title_and_description() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val scheduleDao: ScheduleDao = db.scheduleDao()

        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "旧标题", createdAt = 1_000L))
        }

        val vm = TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao)

        runBlocking { vm.updateTaskInfo("新标题", "新描述") }

        val task = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(task!!.title).isEqualTo("新标题")
        assertThat(task.description).isEqualTo("新描述")
    }
}
