package com.zhixing.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented TDD (on-device): Subproject 持久化链路。
 *
 * 验证：
 *   - 子项目按 task FK 插入
 *   - 按 taskId 查回该任务下所有子项目
 *   - 状态字段完整保留
 */
@RunWith(AndroidJUnit4::class)
class RoomSubprojectPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SubprojectDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.subprojectDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_subproject_with_task_fk_and_query_by_taskId() {
        runBlocking {
            val taskDao = db.taskDao()
            val taskId = taskDao.insertTask(
                com.zhixing.data.entity.TaskEntity(title = "读书笔记", createdAt = 1_000L),
            )

            val sub = SubprojectEntity(
                taskId = taskId,
                title = "选书目",
                status = "backlog",
                estimatedDuration = 30,
                createdAt = 2_000L,
            )

            val id = dao.insertSubproject(sub)
            assertThat(id).isGreaterThan(0)

            val list = dao.getSubprojectsByTaskId(taskId).first()
            assertThat(list).hasSize(1)
            assertThat(list[0].title).isEqualTo("选书目")
            assertThat(list[0].status).isEqualTo("backlog")
            assertThat(list[0].estimatedDuration).isEqualTo(30)
            assertThat(list[0].taskId).isEqualTo(taskId)
        }
    }

    @Test
    fun update_subproject_status_and_clear_schedule_on_abandon() {
        runBlocking {
            val taskDao: TaskDao = db.taskDao()
            val scheduleDao: ScheduleDao = db.scheduleDao()

            val taskId = taskDao.insertTask(TaskEntity(title = "放弃的任务", createdAt = 1_000L))
            val subId = dao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "子项目 A", status = "已排期", createdAt = 2_000L),
            )
            scheduleDao.insertScheduleItem(
                ScheduleEntity(subprojectId = subId, date = "2026-07-08", startTime = 540, endTime = 600),
            )

            dao.updateSubprojectStatus(subId, "已放弃")
            scheduleDao.clearScheduleForSubproject(subId)

            val updated = dao.getSubprojectsByTaskId(taskId).first()
            assertThat(updated[0].status).isEqualTo("已放弃")

            val scheduleItems = scheduleDao.getScheduleItemsByDate("2026-07-08").first()
            assertThat(scheduleItems).isEmpty()
        }
    }

    @Test
    fun delete_task_cascades_to_its_subprojects() {
        runBlocking {
            val taskDao: TaskDao = db.taskDao()
            val taskId = taskDao.insertTask(TaskEntity(title = "要被删除的任务", createdAt = 1_000L))

            dao.insertSubproject(SubprojectEntity(taskId = taskId, title = "子项目 A", createdAt = 2_000L))
            dao.insertSubproject(SubprojectEntity(taskId = taskId, title = "子项目 B", createdAt = 3_000L))

            assertThat(dao.getSubprojectsByTaskId(taskId).first()).hasSize(2)

            taskDao.deleteTaskById(taskId)

            assertThat(dao.getSubprojectsByTaskId(taskId).first()).isEmpty()
        }
    }
}
