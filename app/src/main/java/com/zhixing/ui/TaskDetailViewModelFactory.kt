package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao

class TaskDetailViewModelFactory(
    private val taskId: Long,
    private val taskDao: TaskDao,
    private val subprojectDao: SubprojectDao,
    private val scheduleDao: ScheduleDao,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TaskDetailViewModel(taskId, taskDao, subprojectDao, scheduleDao) as T
    }
}
