package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhixing.data.ScheduleConflictDetector
import com.zhixing.data.SubprojectState
import com.zhixing.data.TaskStatus
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 日视图 ViewModel。
 *
 * 按日期加载排期项，并与子项目 join 得到展示数据。
 */
class DayScheduleViewModel(
    private val date: String,
    private val taskDao: TaskDao,
    private val scheduleDao: ScheduleDao,
    private val subprojectDao: SubprojectDao,
    private val currentTimeProvider: () -> Int = { currentMinutesOfDay() },
) : ViewModel() {

    private val _scheduleItems = MutableStateFlow<List<ScheduleItem>>(emptyList())
    val scheduleItems: StateFlow<List<ScheduleItem>> = _scheduleItems.asStateFlow()

    private val _backlogItems = MutableStateFlow<List<BacklogItem>>(emptyList())
    val backlogItems: StateFlow<List<BacklogItem>> = _backlogItems.asStateFlow()

    // 当日当前分钟数，每 60 秒刷新一次供逾期判定使用。
    private val _currentMinutes = MutableStateFlow(currentTimeProvider())

    init {
        // 定时刷新"当日分钟数"，让逾期状态随时间推进自动更新。
        viewModelScope.launch {
            while (true) {
                _currentMinutes.value = currentTimeProvider()
                delay(60_000L)
            }
        }

        viewModelScope.launch {
            combine(
                scheduleDao.getScheduleItemsByDate(date),
                subprojectDao.getAllSubprojects(),
                taskDao.getAllTasks(),
                _currentMinutes,
            ) { scheduleItems, subprojects, tasks, currentTime ->
                val assembled = ScheduleListComposer.assemble(
                    scheduleItems = scheduleItems,
                    subprojects = subprojects,
                    currentTime = currentTime,
                    today = date,
                )
                val scheduledIds = scheduleItems.map { it.subprojectId }.toSet()
                val taskTitles = tasks.associate { it.id to it.title }
                val backlog = BacklogComposer.assemble(subprojects, scheduledIds, taskTitles)
                Pair(assembled, backlog)
            }.collect { (assembled, backlog) ->
                _scheduleItems.value = assembled
                _backlogItems.value = backlog
            }
        }
    }

    /**
     * 排期动作：把子项目放到指定日期/时间段。
     *
     * 先做同任务时间冲突检测，冲突则拒绝（返回 false）；
     * 否则写入 schedule_items + 把子项目状态改为"已排期"。
     *
     * @return 是否成功排期
     */
    suspend fun scheduleSubproject(subprojectId: Long, date: String, startTime: Int, endTime: Int): Boolean {
        val existing = scheduleDao.getScheduleItemsByDate(date).first()
        val subprojects = subprojectDao.getAllSubprojects().first()
        if (ScheduleConflictDetector.hasConflict(
                subprojectId = subprojectId,
                date = date,
                startTime = startTime,
                endTime = endTime,
                existingSchedules = existing,
                subprojects = subprojects,
            )
        ) {
            return false
        }
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
        return true
    }

    /**
     * 标记子项目"已完成"（日程视图点击项目块触发）。
     *
     * 先验证流转合法性，再写子项目状态 + 完成时间戳；
     * 若该任务的所有子项目都进入终态，任务自动变"已完成"。
     */
    suspend fun completeSubproject(subprojectId: Long) {
        val all = subprojectDao.getAllSubprojects().first()
        val current = all.first { it.id == subprojectId }
        val newStatus = SubprojectState.transition(current.status, "已完成")
        val completedAt = System.currentTimeMillis()
        subprojectDao.updateSubprojectStatusAndCompletedAt(subprojectId, newStatus, completedAt)
        // 任务自动完成：所有子项目终态 → 任务变"已完成"（仅在任务本身还不是终态时回写）
        val taskId = current.taskId
        val task = taskDao.getTaskById(taskId) ?: return
        val after = all.map { if (it.id == subprojectId) it.copy(status = newStatus) else it }
        if (task.status !in TERMINAL && TaskStatus.resolve(after.map { it.status }) == "已完成") {
            taskDao.updateTaskStatus(taskId, "已完成")
        }
    }

    /**
     * 标记子项目"已放弃"（日程视图点击项目块触发）。
     *
     * 先验证流转合法性（已是"已放弃"则拒绝，返回 false），
     * 再写子项目状态 + 完成时间戳；
     * 若该任务的所有子项目都进入终态，任务自动变"已完成"。
     */
    suspend fun abandonSubproject(subprojectId: Long): Boolean {
        val all = subprojectDao.getAllSubprojects().first()
        val current = all.first { it.id == subprojectId }
        val newStatus = try {
            SubprojectState.transition(current.status, "已放弃")
        } catch (e: IllegalArgumentException) {
            return false
        }
        val completedAt = System.currentTimeMillis()
        subprojectDao.updateSubprojectStatusAndCompletedAt(subprojectId, newStatus, completedAt)
        // 任务自动完成：所有子项目终态 → 任务变"已完成"（仅在任务本身还不是终态时回写）
        val taskId = current.taskId
        val task = taskDao.getTaskById(taskId) ?: return true
        val after = all.map { if (it.id == subprojectId) it.copy(status = newStatus) else it }
        if (task.status !in TERMINAL && TaskStatus.resolve(after.map { it.status }) == "已完成") {
            taskDao.updateTaskStatus(taskId, "已完成")
        }
        return true
    }

    private val TERMINAL = setOf("已完成", "已放弃")

}

/** 拖放排期默认时长（分钟）：优先 estimatedDuration，fallback 一格（30 分钟）。 */
fun resolveScheduledDuration(subproject: SubprojectEntity, slotMinutes: Int = 30): Int {
    val estimated = subproject.estimatedDuration
    return if (estimated != null && estimated > 0) estimated else slotMinutes
}

private fun currentMinutesOfDay(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}

class DayScheduleViewModelFactory(
    private val date: String,
    private val taskDao: TaskDao,
    private val scheduleDao: ScheduleDao,
    private val subprojectDao: SubprojectDao,
    private val currentTimeProvider: () -> Int = { currentMinutesOfDay() },
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DayScheduleViewModel(date, taskDao, scheduleDao, subprojectDao, currentTimeProvider) as T
    }
}
