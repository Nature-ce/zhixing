package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao

class ReviewViewModelFactory(
    private val taskDao: TaskDao,
    private val subprojectDao: SubprojectDao,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ReviewViewModel(taskDao, subprojectDao) as T
    }
}
