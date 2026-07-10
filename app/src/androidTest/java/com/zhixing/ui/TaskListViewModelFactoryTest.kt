package com.zhixing.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.db.AppDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * TDD: TaskListViewModelFactory 构造注入 DAO → 产出 VM。
 *
 * 验证 factory.create(TaskListViewModel::class.java) 返回的 VM 持有正确的 DAO。
 * 用真实 in-memory Room 验证集成。
 */
class TaskListViewModelFactoryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun factory_creates_view_model_with_correct_daos() {
        val taskDao: TaskDao = db.taskDao()
        val subprojectDao: SubprojectDao = db.subprojectDao()
        val factory = TaskListViewModelFactory(taskDao, subprojectDao)

        val vm = factory.create(TaskListViewModel::class.java)

        assertThat(vm).isNotNull
        assertThat(vm.taskItems.value).isEmpty()
    }
}
