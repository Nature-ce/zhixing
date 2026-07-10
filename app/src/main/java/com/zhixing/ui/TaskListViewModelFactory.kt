package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao

/**
 * TaskListViewModel 工厂。
 *
 * 构造注入 DAO 给 VM。实现 ViewModelProvider.Factory 接口，
 * 供 viewModel(factory = ...) 使用。
 */
class TaskListViewModelFactory(
    private val taskDao: TaskDao,
    private val subprojectDao: SubprojectDao,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TaskListViewModel(taskDao, subprojectDao) as T
    }
}
