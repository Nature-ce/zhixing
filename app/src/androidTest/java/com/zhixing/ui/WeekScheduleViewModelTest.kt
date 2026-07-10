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
 * Instrumented TDD: WeekScheduleViewModel 按周加载排期。
 */
class WeekScheduleViewModelTest {

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
    fun loads_schedule_items_grouped_by_date() {
        runBlocking {
            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val sub1 = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "选书目", status = "已排期", createdAt = 2_000L))
            val sub2 = subprojectDao.insertSubproject(SubprojectEntity(taskId = taskId, title = "划重点", status = "已排期", createdAt = 3_000L))
            // 周一 2026-07-06
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = sub1, date = "2026-07-06", startTime = 600, endTime = 660))
            // 周三 2026-07-08
            scheduleDao.insertScheduleItem(ScheduleEntity(subprojectId = sub2, date = "2026-07-08", startTime = 540, endTime = 600))
        }

        val vm = WeekScheduleViewModel(weekDates = listOf("2026-07-06", "2026-07-07", "2026-07-08", "2026-07-09", "2026-07-10", "2026-07-11", "2026-07-12"), scheduleDao = scheduleDao, subprojectDao = subprojectDao)

        val itemsByDate = runBlocking { vm.itemsByDate.first { it.isNotEmpty() } }

        // 周一和周三都有排期
        assertThat(itemsByDate["2026-07-06"]).hasSize(1)
        assertThat(itemsByDate["2026-07-06"]!![0].subprojectTitle).isEqualTo("选书目")
        assertThat(itemsByDate["2026-07-08"]).hasSize(1)
        assertThat(itemsByDate["2026-07-08"]!![0].subprojectTitle).isEqualTo("划重点")
        // 周日无排期，不在 map 里
        assertThat(itemsByDate["2026-07-07"]).isNull()
    }
}
