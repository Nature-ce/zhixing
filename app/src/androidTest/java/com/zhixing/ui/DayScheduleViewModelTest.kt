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
 * Instrumented TDD (on-device): DayScheduleViewModel 按日期加载排期。
 *
 * 验证：
 *   - 按日期查询 schedule_items，组装成 ScheduleItem（含子项目标题）
 *   - 只返回指定日期的排期
 */
class DayScheduleViewModelTest {

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
    fun loads_schedule_items_for_given_date() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val subId = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L))
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = subId, date = "2026-07-08", startTime = 600, endTime = 660, createdAt = 3_000L))
        }

        val vm = DayScheduleViewModel(date = "2026-07-08", taskDao = taskDao, scheduleDao = scheduleDao, subprojectDao = subprojectDao)

        val items = runBlocking { vm.scheduleItems.first { it.isNotEmpty() } }

        assertThat(items).hasSize(1)
        assertThat(items[0].subprojectTitle).isEqualTo("选书目")
        assertThat(items[0].startTime).isEqualTo(600)
        assertThat(items[0].endTime).isEqualTo(660)
    }

    @Test
    fun does_not_return_items_from_other_dates() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val subId = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L))
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = subId, date = "2026-07-09", startTime = 600, endTime = 660))
        }

        val vm = DayScheduleViewModel(date = "2026-07-08", taskDao = taskDao, scheduleDao = scheduleDao, subprojectDao = subprojectDao)

        // 等 flow 首次 emit（空列表）
        val items = runBlocking { vm.scheduleItems.first() }

        assertThat(items).isEmpty()
    }

    @Test
    fun marks_items_as_overdue_when_endTime_before_injected_current_time() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val subId = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L))
            // 10:00-11:00，注入"当前时间"为 12:00（720 分钟）→ 逾期
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = subId, date = "2026-07-08", startTime = 600, endTime = 660))
        }

        // 注入固定时间提供者，使测试不依赖真实时钟
        val vm = DayScheduleViewModel(
            date = "2026-07-08",
            taskDao = taskDao,
            scheduleDao = scheduleDao,
            subprojectDao = subprojectDao,
            currentTimeProvider = { 720 },
        )

        val items = runBlocking { vm.scheduleItems.first { it.isNotEmpty() } }

        assertThat(items).hasSize(1)
        assertThat(items[0].subprojectTitle).isEqualTo("选书目")
        assertThat(items[0].isOverdue).isTrue()
    }
}
