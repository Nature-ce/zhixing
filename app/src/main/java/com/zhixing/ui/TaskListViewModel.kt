package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.TaskEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 任务列表 ViewModel。桥接 Room DAO 和 UI 层。
 *
 * 观察 tasks flow，每次 tasks 变更时重新组装 List<TaskListItem>。
 * 子项目状态变化通过 subprojects Flow 的首次 emit 获取当前值。
 *
 * 继承 androidx.lifecycle.ViewModel，使用 viewModelScope 管理协程生命周期。
 */
class TaskListViewModel(
    private val taskDao: TaskDao,
    private val subprojectDao: SubprojectDao,
) : ViewModel() {
    private val _taskItems = MutableStateFlow<List<TaskListItem>>(emptyList())
    val taskItems: StateFlow<List<TaskListItem>> = _taskItems.asStateFlow()

    // 数据库中是否有任何任务（包括已完成/已放弃），用于空状态文案区分
    private val _hasAnyTask = MutableStateFlow(false)
    val hasAnyTask: StateFlow<Boolean> = _hasAnyTask.asStateFlow()

    // 终态任务不进任务列表，去回顾页
    private val TERMINAL = setOf("已完成", "已放弃")

    init {
        viewModelScope.launch {
            taskDao.getAllTasks().collect { tasks ->
                _hasAnyTask.value = tasks.isNotEmpty()
                val items = tasks
                    .filter { it.status !in TERMINAL }
                    .map { task ->
                        val subprojects = subprojectDao.getSubprojectsByTaskId(task.id).first()
                        val assembled = TaskListComposer.assemble(task, subprojects)
                        // 业务规则：所有子项目终态 → 任务自动完成，回写数据库
                        // 仅在任务本身还不是终态时回写，避免覆盖"已放弃"状态
                        if (task.status !in TERMINAL && assembled.statusLabel == "已完成") {
                            taskDao.updateTaskStatus(task.id, "已完成")
                        }
                        assembled
                    }
                _taskItems.value = items
            }
        }
    }

    fun addTask(title: String) {
        viewModelScope.launch {
            taskDao.insertTask(
                TaskEntity(title = title, createdAt = System.currentTimeMillis()),
            )
        }
    }
}
