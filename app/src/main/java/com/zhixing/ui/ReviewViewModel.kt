package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 回顾 tab ViewModel。
 *
 * 加载所有任务，筛选出已完成/已放弃的，组装成 TaskListItem。
 */
class ReviewViewModel(
    private val taskDao: TaskDao,
    private val subprojectDao: SubprojectDao,
) : ViewModel() {

    private val _reviewItems = MutableStateFlow<List<TaskListItem>>(emptyList())
    val reviewItems: StateFlow<List<TaskListItem>> = _reviewItems.asStateFlow()

    init {
        viewModelScope.launch {
            taskDao.getAllTasks().collect { tasks ->
                val items = tasks
                    .map { task ->
                        val subprojects = subprojectDao.getSubprojectsByTaskId(task.id).first()
                        task to subprojects
                    }
                    .filter { (task, subprojects) ->
                        ReviewComposer.isReviewUnlocked(task, subprojects)
                    }
                    .map { (task, subprojects) ->
                        TaskListComposer.assemble(task, subprojects)
                    }
                _reviewItems.value = items
            }
        }
    }
}
