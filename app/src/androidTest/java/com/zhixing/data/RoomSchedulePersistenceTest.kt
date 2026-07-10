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
 * Instrumented TDD (on-device): Schedule 持久化链路。
 *
 * 验证：
 *   - 日程项按子项目 FK + 日期插入
 *   - 按日期查回当天所有日程项
 *   - 跨日项在不同日期各自独立
 */
@RunWith(AndroidJUnit4::class)
class RoomSchedulePersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ScheduleDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.scheduleDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_schedule_item_and_query_by_date() {
        runBlocking {
            val taskDao: TaskDao = db.taskDao()
            val subprojectDao: SubprojectDao = db.subprojectDao()

            val taskId = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            val subprojectId = subprojectDao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "选书目", createdAt = 2_000L),
            )

            val item = ScheduleEntity(
                subprojectId = subprojectId,
                date = "2026-07-08",
                startTime = 540,   // 09:00
                endTime = 600,     // 10:00
            )

            val id = dao.insertScheduleItem(item)
            assertThat(id).isGreaterThan(0)

            val dayItems = dao.getScheduleItemsByDate("2026-07-08").first()
            assertThat(dayItems).hasSize(1)
            assertThat(dayItems[0].subprojectId).isEqualTo(subprojectId)
            assertThat(dayItems[0].startTime).isEqualTo(540)
            assertThat(dayItems[0].endTime).isEqualTo(600)

            val otherDay = dao.getScheduleItemsByDate("2026-07-09").first()
            assertThat(otherDay).isEmpty()
        }
    }
}
