package com.zhixing.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

/**
 * Instrumented TDD (on-device): DayScheduleViewModel backlog 加载。
 *
 * 验证：
 *   - backlogItems 只包含 backlog 状态且当天未排期的子项目
 */
class DayScheduleBacklogTest {

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
    fun backlog_items_contain_only_backlog_subprojects_not_scheduled_today() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "backlog", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "划重点", status = "backlog", createdAt = 3_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "写笔记", status = "已完成", createdAt = 4_000L))
            // 子项目 1 今天已排期
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = 1, date = "2026-07-08", startTime = 600, endTime = 660))
        }

        val vm = DayScheduleViewModel(date = "2026-07-08", taskDao = taskDao, scheduleDao = scheduleDao, subprojectDao = subprojectDao)

        val backlog = runBlocking { vm.backlogItems.first { it.isNotEmpty() } }

        // 只有"划重点"：backlog 状态且今天未排期
        assertThat(backlog).hasSize(1)
        assertThat(backlog[0].id).isEqualTo(2L)
        assertThat(backlog[0].title).isEqualTo("划重点")
    }
}
