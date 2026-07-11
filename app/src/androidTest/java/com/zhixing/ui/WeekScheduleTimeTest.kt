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
 * 回归测试：排期时间在周视图数据路径中保持原值。
 *
 * 验证 11:00-12:00 (660-720) 经 getScheduleItemsBetween +
 * WeekScheduleViewModel 后 startTime/endTime 不变（无 -30min 偏移）。
 */
class WeekScheduleTimeTest {

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
    fun dao_getScheduleItemsBetween_preserves_startTime() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L))
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = sub, date = "2026-07-08", startTime = 660, endTime = 720))
        }

        val items = runBlocking { scheduleDao.getScheduleItemsBetween("2026-07-06", "2026-07-12").first() }

        assertThat(items).hasSize(1)
        assertThat(items[0].startTime).isEqualTo(660)
        assertThat(items[0].endTime).isEqualTo(720)
    }

    @Test
    fun vm_itemsByDate_preserves_startTime() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L))
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = sub, date = "2026-07-08", startTime = 660, endTime = 720))
        }

        val vm = WeekScheduleViewModel(
            weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"),
            scheduleDao = scheduleDao,
            subprojectDao = subprojectDao,
            taskDao = taskDao,
        )

        val itemsByDate = runBlocking { vm.itemsByDate.first { it.isNotEmpty() } }
        val item = itemsByDate["2026-07-08"]!![0]

        assertThat(item.startTime).isEqualTo(660)
        assertThat(item.endTime).isEqualTo(720)
    }
}
