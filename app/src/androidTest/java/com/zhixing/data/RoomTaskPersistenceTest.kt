package com.zhixing.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.db.AppDatabase
import com.zhixing.data.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented TDD (on-device): Room 持久化链路。
 *
 * 验证整条 path 在真实 Android 环境可用：
 *   - in-memory Room database 构建
 *   - @Insert 写入 + 主键自增生效
 *   - @Query 按 id 读回，字段完整保留
 *   - Flow emit 列表数据
 *
 * 纯 infrastructure 测试，零业务逻辑。用 in-memory DB，跑完即销毁，
 * 不影响 app 真实数据库。
 */
@RunWith(AndroidJUnit4::class)
class RoomTaskPersistenceTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TaskDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.taskDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insert_generates_auto_increment_id_and_read_back_preserves_all_fields() {
        runBlocking {
            val task = TaskEntity(
                title = "完成 PRD 撰写",
                description = "把产品需求整理成 PRD",
                status = "进行中",
                targetCompletionDate = "2026-07-15",
                createdAt = 1_720_000_000L,
                completedAt = null,
            )

            val id = dao.insertTask(task)

            assertThat(id).isGreaterThan(0)

            val loaded = dao.getTaskById(id)
            assertThat(loaded).isNotNull
            assertThat(loaded!!.title).isEqualTo("完成 PRD 撰写")
            assertThat(loaded.description).isEqualTo("把产品需求整理成 PRD")
            assertThat(loaded.status).isEqualTo("进行中")
            assertThat(loaded.targetCompletionDate).isEqualTo("2026-07-15")
            assertThat(loaded.createdAt).isEqualTo(1_720_000_000L)
            assertThat(loaded.completedAt).isNull()
        }
    }

    @Test
    fun getAllTasks_flow_emits_inserted_tasks_ordered_by_createdAt_desc() {
        runBlocking {
            val older = TaskEntity(title = "旧任务", createdAt = 1_000L)
            val newer = TaskEntity(title = "新任务", createdAt = 2_000L)

            dao.insertTask(older)
            dao.insertTask(newer)

            val all = dao.getAllTasks().first()

            assertThat(all).hasSize(2)
            assertThat(all[0].title).isEqualTo("新任务")
            assertThat(all[1].title).isEqualTo("旧任务")
        }
    }
}
