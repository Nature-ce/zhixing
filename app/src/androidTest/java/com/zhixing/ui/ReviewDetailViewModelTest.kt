package com.zhixing.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
 * TDD (RED->GREEN): ReviewDetailViewModel。
 *
 * 验证：
 *   - 加载任务标题 + 子项目
 *   - reviewText 初始为空，onReviewChange 更新它
 */
class ReviewDetailViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var taskDao: TaskDao
    private lateinit var subprojectDao: SubprojectDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        taskDao = db.taskDao()
        subprojectDao = db.subprojectDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun loads_task_title_and_subprojects() {
        val taskId = runBlocking {
            val id = taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "选书目", status = "已完成", createdAt = 2_000L))
            subprojectDao.insertSubproject(SubprojectEntity(taskId = id, title = "划重点", status = "已完成", createdAt = 3_000L))
            id
        }

        val vm = ReviewDetailViewModel(taskId, taskDao, subprojectDao)

        val title = runBlocking { vm.taskTitle.first { it.isNotEmpty() } }
        assertThat(title).isEqualTo("读书笔记")

        val subs = runBlocking { vm.subprojects.first { it.isNotEmpty() } }
        assertThat(subs).hasSize(2)
        assertThat(subs[0].title).isEqualTo("选书目")
        assertThat(subs[1].title).isEqualTo("划重点")
    }

    @Test
    fun reviewText_updates_on_change() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }

        val vm = ReviewDetailViewModel(taskId, taskDao, subprojectDao)

        assertThat(vm.reviewText.value).isEmpty()

        vm.onReviewChange("收获很大")

        assertThat(vm.reviewText.value).isEqualTo("收获很大")
    }

    @Test
    fun reviewText_persists_to_database() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", createdAt = 1_000L))
        }

        val vm = ReviewDetailViewModel(taskId, taskDao, subprojectDao)
        vm.onReviewChange("收获很大")

        // 等 flow 反映到数据库（onReviewChange 异步写 DB）
        runBlocking {
            var text = ""
            while (text.isEmpty()) {
                text = taskDao.getTaskById(taskId)?.reviewText ?: ""
                if (text.isEmpty()) Thread.sleep(50)
            }
        }

        val saved = runBlocking { taskDao.getTaskById(taskId) }
        assertThat(saved!!.reviewText).isEqualTo("收获很大")
    }

    @Test
    fun reviewText_loads_from_database() {
        val taskId = runBlocking {
            taskDao.insertTask(TaskEntity(title = "读书笔记", reviewText = "之前写的回顾", createdAt = 1_000L))
        }

        val vm = ReviewDetailViewModel(taskId, taskDao, subprojectDao)

        val text = runBlocking { vm.reviewText.first { it.isNotEmpty() } }
        assertThat(text).isEqualTo("之前写的回顾")
    }
}
