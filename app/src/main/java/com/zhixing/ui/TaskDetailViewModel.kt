package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhixing.data.SubprojectState
import com.zhixing.data.TaskStatus
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 任务详情 ViewModel。
 *
 * 按 taskId 加载子项目 + 处理状态流转（用 SubprojectState 领域逻辑）。
 * 非法流转由 SubprojectState.transition 抛 IllegalArgumentException。
 */
class TaskDetailViewModel(
    private val taskId: Long,
    private val taskDao: TaskDao,
    private val subprojectDao: SubprojectDao,
    private val scheduleDao: ScheduleDao,
) : ViewModel() {

    private val _taskTitle = MutableStateFlow("")
    val taskTitle: StateFlow<String> = _taskTitle.asStateFlow()

    private val _taskDescription = MutableStateFlow<String?>(null)
    val taskDescription: StateFlow<String?> = _taskDescription.asStateFlow()

    private val _subprojects = MutableStateFlow<List<SubprojectEntity>>(emptyList())
    val subprojects: StateFlow<List<SubprojectEntity>> = _subprojects.asStateFlow()

    init {
        viewModelScope.launch {
            val task = taskDao.getTaskById(taskId)
            _taskTitle.value = task?.title ?: ""
            _taskDescription.value = task?.description
        }
        viewModelScope.launch {
            subprojectDao.getSubprojectsByTaskId(taskId).collect { list ->
                _subprojects.value = list
            }
        }
    }

    suspend fun changeSubprojectStatus(subprojectId: Long, to: String) {
        val current = _subprojects.value.first { it.id == subprojectId }
        // SubprojectState.transition 验证流转合法性，非法则抛异常
        val newStatus = SubprojectState.transition(current.status, to)
        // 进入终态时记录完成时间戳；离开终态（误操作撤销）时清零
        val completedAt = if (to in TERMINAL) System.currentTimeMillis() else null
        subprojectDao.updateSubprojectStatusAndCompletedAt(subprojectId, newStatus, completedAt)
        // 任务自动完成：所有子项目都进入终态 → 任务变"已完成"（仅在任务本身还不是终态时回写）
        val after = _subprojects.value.map { if (it.id == subprojectId) it.copy(status = newStatus) else it }
        if (taskDao.getTaskById(taskId)?.status !in TERMINAL && TaskStatus.resolve(after.map { it.status }) == "已完成") {
            taskDao.updateTaskStatus(taskId, "已完成")
        }
    }

    fun addSubproject(title: String) {
        viewModelScope.launch {
            subprojectDao.insertSubproject(
                SubprojectEntity(
                    taskId = taskId,
                    title = title,
                    status = "backlog",
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun scheduleSubproject(subprojectId: Long, date: String, startTime: Int, endTime: Int) {
        viewModelScope.launch {
            // 先验证流转合法性（backlog/已完成 → 已排期）
            val current = _subprojects.value.first { it.id == subprojectId }
            SubprojectState.transition(current.status, "已排期")
            // 插入排期 + 更新状态
            scheduleDao.insertScheduleItem(
                ScheduleEntity(
                    subprojectId = subprojectId,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            subprojectDao.updateSubprojectStatus(subprojectId, "已排期")
        }
    }

    private val TERMINAL = setOf("已完成", "已放弃")

    suspend fun abandonTask() {
        val current = _subprojects.value
        // 所有子项目变"已放弃"
        subprojectDao.updateAllSubprojectsStatus(taskId, "已放弃")
        // 清掉这些子项目的日程占用
        scheduleDao.clearScheduleForSubprojects(current.map { it.id })
        // 任务状态变"已放弃"
        taskDao.updateTaskStatus(taskId, "已放弃")
    }

    suspend fun deleteTask() {
        // FK cascade：删任务 → 子项目自动删 → 日程自动删
        taskDao.deleteTaskById(taskId)
    }

    suspend fun updateTaskInfo(title: String, description: String?) {
        taskDao.updateTaskInfo(taskId, title, description)
        _taskTitle.value = title
        _taskDescription.value = description
    }
}
