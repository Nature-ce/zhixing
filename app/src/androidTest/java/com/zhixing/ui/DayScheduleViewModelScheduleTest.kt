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
 * Instrumented TDD: DayScheduleViewModel 排期动作（拖放落地的结果）。
 *
 * 验证:
 *   - 排期一个 backlog 子项目 → schedule_items 写入 + 子项目状态变"已排期" + backlog 减少
 *   - 同任务时间冲突 → 排期被拒绝，数据不变
 */
class DayScheduleViewModelScheduleTest {

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

    @Test
    fun schedule_backlog_subproject_writes_schedule_and_updates_status() {
        runBlocking {
        val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        val sub = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L))

        val vm = DayScheduleViewModel(
            date = "2026-07-09",
            taskDao = taskDao,
            scheduleDao = scheduleDao,
            subprojectDao = subprojectDao,
            currentTimeProvider = { 0 },
        )

        val result = vm.scheduleSubproject(sub, "2026-07-09", 540, 600)

        assertThat(result).isEqualTo(true)
        // 子项目状态变为"已排期"
        val updated = subprojectDao.getAllSubprojects().first().first { it.id == sub }
        assertThat(updated.status).isEqualTo("已排期")
        // schedule_items 写入一条
        val items = scheduleDao.getScheduleItemsByDate("2026-07-09").first()
        assertThat(items).hasSize(1)
        assertThat(items[0].startTime).isEqualTo(540)
        assertThat(items[0].endTime).isEqualTo(600)
        // backlog 减少
        val backlog = vm.backlogItems.first { it.size <= 1 }
        assertThat(backlog.map { it.id }).doesNotContain(sub)
        }
    }

    @Test
    fun schedule_rejects_when_same_task_time_conflicts() {
        runBlocking {
        val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        val sub1 = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L))
        val sub2 = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "划重点", status = "backlog", createdAt = 3_000L))
        // sub1 已排 9:00-10:00
        scheduleDao.insertScheduleItem(com.zhixing.data.entity.ScheduleEntity(subprojectId = sub1, date = "2026-07-09", startTime = 540, endTime = 600, createdAt = 4_000L))

        val vm = DayScheduleViewModel(
            date = "2026-07-09",
            taskDao = taskDao,
            scheduleDao = scheduleDao,
            subprojectDao = subprojectDao,
            currentTimeProvider = { 0 },
        )

        // sub2 同任务，排 9:00-10:00 → 冲突，应拒绝
        val result = vm.scheduleSubproject(sub2, "2026-07-09", 540, 600)

        assertThat(result).isEqualTo(false)
        // sub2 状态不变
        val updated = subprojectDao.getAllSubprojects().first().first { it.id == sub2 }
        assertThat(updated.status).isEqualTo("backlog")
        // schedule_items 仍只有 sub1 一条
        val items = scheduleDao.getScheduleItemsByDate("2026-07-09").first()
        assertThat(items).hasSize(1)
        }
    }

    @Test
    fun abandon_subproject_changes_status_to_abandoned() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub = subprojectDao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L)
            )

            val vm = DayScheduleViewModel(
                date = "2026-07-09",
                taskDao = taskDao,
                scheduleDao = scheduleDao,
                subprojectDao = subprojectDao,
                currentTimeProvider = { 0 },
            )

            val result = vm.abandonSubproject(sub)

            assertThat(result).isEqualTo(true)
            val updated = subprojectDao.getAllSubprojects().first().first { it.id == sub }
            assertThat(updated.status).isEqualTo("已放弃")
        }
    }

    @Test
    fun abandon_rejects_when_already_abandoned() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub = subprojectDao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "选书目", status = "已放弃", createdAt = 2_000L)
            )

            val vm = DayScheduleViewModel(
                date = "2026-07-09",
                taskDao = taskDao,
                scheduleDao = scheduleDao,
                subprojectDao = subprojectDao,
                currentTimeProvider = { 0 },
            )

            val result = vm.abandonSubproject(sub)

            assertThat(result).isEqualTo(false)
            val updated = subprojectDao.getAllSubprojects().first().first { it.id == sub }
            assertThat(updated.status).isEqualTo("已放弃")
        }
    }

    @Test
    fun unschedule_subproject_reverts_status_and_clears_schedule() {
        // 端到端：排期后子项目出现在日程视图 → 回退 → 状态变回 backlog + 排期记录删除 + 重入 backlog。
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub = subprojectDao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L)
            )

            val vm = DayScheduleViewModel(
                date = "2026-07-09",
                taskDao = taskDao,
                scheduleDao = scheduleDao,
                subprojectDao = subprojectDao,
                currentTimeProvider = { 0 },
            )

            // 排期，等待状态稳定为"已排期"。
            assertThat(vm.scheduleSubproject(sub, "2026-07-09", 540, 600)).isEqualTo(true)
            runBlocking {
                vm.scheduleItems.first { items ->
                    items.any { it.subprojectId == sub && it.subprojectStatus == "已排期" }
                }
            }
            assertThat(scheduleDao.getScheduleItemsByDate("2026-07-09").first()).hasSize(1)

            // 回退 → 返回 true
            val result = vm.unscheduleSubproject(sub)
            assertThat(result).isEqualTo(true)

            // 子项目状态变回 backlog
            val updated = subprojectDao.getAllSubprojects().first().first { it.id == sub }
            assertThat(updated.status).isEqualTo("backlog")
            // 排期记录删除
            assertThat(scheduleDao.getScheduleItemsByDate("2026-07-09").first()).isEmpty()
            // 重入 backlog
            runBlocking {
                vm.backlogItems.first { items -> items.any { it.id == sub } }
            }
            assertThat(vm.backlogItems.value.map { it.id }).contains(sub)
        }
    }

    @Test
    fun unschedule_rejects_when_not_scheduled() {
        // 未排期的子项目（backlog / 已完成 / 已放弃）不允许回退，返回 false 且数据不变。
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub = subprojectDao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L)
            )

            val vm = DayScheduleViewModel(
                date = "2026-07-09",
                taskDao = taskDao,
                scheduleDao = scheduleDao,
                subprojectDao = subprojectDao,
                currentTimeProvider = { 0 },
            )

            val result = vm.unscheduleSubproject(sub)

            assertThat(result).isEqualTo(false)
            val updated = subprojectDao.getAllSubprojects().first().first { it.id == sub }
            assertThat(updated.status).isEqualTo("backlog")
        }
    }

    @Test
    fun abandon_subproject_updates_schedule_items_status_to_abandoned() {
        // 端到端：排期后子项目出现在日程视图（已排期）→ 放弃 → 日程视图里状态应变为"已放弃"（卡片随即弱化）。
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub = subprojectDao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L)
            )

            val vm = DayScheduleViewModel(
                date = "2026-07-09",
                taskDao = taskDao,
                scheduleDao = scheduleDao,
                subprojectDao = subprojectDao,
                currentTimeProvider = { 0 },
            )

            // 排期：让它出现在日程视图，并等待状态稳定为"已排期"。
            // 注意：写入 schedule_items 与翻转子项目状态是两次独立的 DB 写入，
            // combine 会分两次发射；必须等到 status 真正变为"已排期"才算排期完成。
            val scheduled = vm.scheduleSubproject(sub, "2026-07-09", 540, 600)
            assertThat(scheduled).isEqualTo(true)
            runBlocking {
                vm.scheduleItems.first { items ->
                    items.any { it.subprojectId == sub && it.subprojectStatus == "已排期" }
                }
            }
            val before = vm.scheduleItems.value.first { it.subprojectId == sub }
            assertThat(before.subprojectStatus).isEqualTo("已排期")

            // 放弃 → 日程视图状态应变更为"已放弃"
            val result = vm.abandonSubproject(sub)
            assertThat(result).isEqualTo(true)
            runBlocking {
                vm.scheduleItems.first { items ->
                    items.any { it.subprojectId == sub && it.subprojectStatus == "已放弃" }
                }
            }
            val after = vm.scheduleItems.value.first { it.subprojectId == sub }
            assertThat(after.subprojectStatus).isEqualTo("已放弃")
        }
    }

    @Test
    fun complete_last_subproject_auto_completes_task() {
        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "已排期", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "已排期", createdAt = 3_000L))
            id
        }

        val vm = DayScheduleViewModel(
            date = "2026-07-09",
            taskDao = taskDao,
            scheduleDao = scheduleDao,
            subprojectDao = subprojectDao,
            currentTimeProvider = { 0 },
        )

        // 等 VM 加载完子项目（无排期行，scheduleItems 为空，不可等其非空）
        runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first { it.size == 2 } }

        val subs = runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first() }
        val first = subs[0]
        val second = subs[1]

        // 标完第一个 → 任务仍为进行中
        runBlocking { vm.completeSubproject(first.id) }
        runBlocking { subprojectDao.getSubprojectsByTaskId(taskId).first { it.any { s -> s.id == first.id && s.status == "已完成" } } }
        val taskAfterFirst = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(taskAfterFirst!!.status).isEqualTo("进行中")

        // 标完第二个（最后一个）→ 任务自动完成（轮询至 task 表 status 稳定）
        runBlocking { vm.completeSubproject(second.id) }
        runBlocking {
            var status = ""
            while (status != "已完成") {
                status = taskDao.getTaskById(taskId)!!.status
            }
        }
        val taskAfterSecond = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(taskAfterSecond!!.status).isEqualTo("已完成")
    }
}
