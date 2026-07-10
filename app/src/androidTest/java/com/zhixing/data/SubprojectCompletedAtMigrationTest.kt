package com.zhixing.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith

/**
 * Instrumented TDD (on-device): completedAt 字段 + v4→v5 迁移。
 *
 * 验证：
 *   - 新增 completedAt 列可写入/读回
 *   - 插入终态子项目时 DAO 新方法同时写入 status + completedAt
 *   - 未调用过新方法的子项目 completedAt 为 null
 */
@RunWith(AndroidJUnit4::class)
class SubprojectCompletedAtMigrationTest {

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
    fun insert_subproject_without_completedAt_defaults_to_null() {
        runBlocking {
            val taskDao = db.taskDao()
            val taskId = taskDao.insertTask(TaskEntity(title = "T", createdAt = 1_000L))
            val subId = dao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "A", status = "backlog", createdAt = 2_000L),
            )

            val list = dao.getSubprojectsByTaskId(taskId).first()
            assertThat(list).hasSize(1)
            assertThat(list[0].completedAt).isNull()
        }
    }

    @Test
    fun updateStatusAndCompletedAt_writes_both_status_and_timestamp() {
        runBlocking {
            val taskDao = db.taskDao()
            val taskId = taskDao.insertTask(TaskEntity(title = "T", createdAt = 1_000L))
            val subId = dao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "A", status = "backlog", createdAt = 2_000L),
            )

            dao.updateSubprojectStatusAndCompletedAt(subId, "已完成", 1_720_000_000_000L)

            val list = dao.getSubprojectsByTaskId(taskId).first()
            assertThat(list[0].status).isEqualTo("已完成")
            assertThat(list[0].completedAt).isEqualTo(1_720_000_000_000L)
        }
    }

    @Test
    fun regular_status_update_does_not_touch_completedAt() {
        runBlocking {
            val taskDao = db.taskDao()
            val taskId = taskDao.insertTask(TaskEntity(title = "T", createdAt = 1_000L))
            val subId = dao.insertSubproject(
                SubprojectEntity(taskId = taskId, title = "A", status = "backlog", createdAt = 2_000L),
            )

            // 走旧方法改状态，completedAt 仍为 null
            dao.updateSubprojectStatus(subId, "已排期")
            val list = dao.getSubprojectsByTaskId(taskId).first()
            assertThat(list[0].status).isEqualTo("已排期")
            assertThat(list[0].completedAt).isNull()
        }
    }
}
