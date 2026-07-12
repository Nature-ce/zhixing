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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 周视图 ViewModel。
 *
 * 按周日期列表加载排期项，join 子项目后按日期分组，
 * 暴露 Map<dateString, List<ScheduleItem>> 供 WeekSchedulePage 渲染。
 */
class WeekScheduleViewModel(
    private val weekDates: List<String>,
    private val scheduleDao: ScheduleDao,
    private val subprojectDao: SubprojectDao,
    private val taskDao: TaskDao,
    private val currentTimeProvider: () -> Int = { currentMinutesOfDay() },
    private val todayProvider: () -> String = { today() },
) : ViewModel() {

    private val _itemsByDate = MutableStateFlow<Map<String, List<ScheduleItem>>>(emptyMap())
    val itemsByDate: StateFlow<Map<String, List<ScheduleItem>>> = _itemsByDate.asStateFlow()

    // 本周 backlog 子项目：status=backlog 且本周 7 天都未排期。
    private val _backlogItems = MutableStateFlow<List<BacklogItem>>(emptyList())
    val backlogItems: StateFlow<List<BacklogItem>> = _backlogItems.asStateFlow()

    // 当日当前分钟数，每 60 秒刷新一次供逾期判定使用，让逾期状态随时间推进自动更新。
    private val _currentMinutes = MutableStateFlow(currentTimeProvider())

    init {
        if (weekDates.isNotEmpty()) {
            val startDate = weekDates.first()
            val endDate = weekDates.last()
            // 定时刷新"当日分钟数"，让逾期状态随时间推进自动更新。
            viewModelScope.launch {
                while (true) {
                    _currentMinutes.value = currentTimeProvider()
                    delay(60_000L)
                }
            }
            viewModelScope.launch {
                combine(
                    scheduleDao.getScheduleItemsBetween(startDate, endDate),
                    subprojectDao.getAllSubprojects(),
                    taskDao.getAllTasks(),
                    _currentMinutes,
                ) { scheduleItems, subprojects, tasks, currentTime ->
                    val assembled = ScheduleListComposer.assemble(
                        scheduleItems = scheduleItems,
                        subprojects = subprojects,
                        currentTime = currentTime,
                        today = todayProvider(),
                    )
                    // 仅保留本周范围内的排期，按日期分组
                    val grouped = assembled
                        .filter { it.date in weekDates }
                        .groupBy { it.date }
                    // 本周已排期的子项目 id 集合
                    val scheduledIds = scheduleItems
                        .filter { it.date in weekDates }
                        .map { it.subprojectId }
                        .toSet()
                    val taskTitles = tasks.associate { it.id to it.title }
                    val backlog = BacklogComposer.assemble(subprojects, scheduledIds, taskTitles)
                    Pair(grouped, backlog)
                }.collect { (grouped, backlog) ->
                    _itemsByDate.value = grouped
                    _backlogItems.value = backlog
                }
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
     * 标记子项目"已完成"。先验证流转合法性，再写状态 + 完成时间戳；
     * 若该任务的所有子项目都进入终态，任务自动变"已完成"。
     */
    suspend fun completeSubproject(subprojectId: Long): Boolean {
        val all = subprojectDao.getAllSubprojects().first()
        val current = all.firstOrNull { it.id == subprojectId } ?: return false
        val newStatus = try {
            SubprojectState.transition(current.status, "已完成")
        } catch (e: IllegalArgumentException) {
            return false
        }
        subprojectDao.updateSubprojectStatusAndCompletedAt(subprojectId, newStatus, System.currentTimeMillis())
        maybeAutoCompleteTask(current.taskId, all, subprojectId, newStatus)
        return true
    }

    /**
     * 放弃子项目 = 彻底删除：清排期记录 + 删子项目行，日程块直接消失，不写 backlog。
     *
     * @return 子项目存在且已删除返回 true；不存在返回 false
     */
    suspend fun abandonSubproject(subprojectId: Long): Boolean {
        val current = subprojectDao.getAllSubprojects().first().firstOrNull { it.id == subprojectId }
            ?: return false
        scheduleDao.clearScheduleForSubproject(subprojectId)
        subprojectDao.deleteSubprojectById(subprojectId)
        return true
    }

    /**
     * 回退已排期的子项目到 backlog。仅当"已排期"才允许：删除排期记录 + 状态改回 backlog。
     */
    suspend fun unscheduleSubproject(subprojectId: Long): Boolean {
        val current = subprojectDao.getAllSubprojects().first().firstOrNull { it.id == subprojectId }
            ?: return false
        val newStatus = try {
            SubprojectState.transition(current.status, "backlog")
        } catch (e: IllegalArgumentException) {
            return false
        }
        scheduleDao.clearScheduleForSubproject(subprojectId)
        subprojectDao.updateSubprojectStatus(subprojectId, newStatus)
        return true
    }

    /**
     * 更新子项目名称与预期时长（inline 面板编辑回写）。
     *
     * @return 子项目存在且已更新返回 true；不存在返回 false
     */
    suspend fun updateSubproject(subprojectId: Long, title: String, estimatedDuration: Int?): Boolean {
        val current = subprojectDao.getAllSubprojects().first().firstOrNull { it.id == subprojectId }
            ?: return false
        subprojectDao.updateSubproject(subprojectId, title, estimatedDuration)
        return true
    }

    /** 任务自动完成：所有子项目终态 → 任务变"已完成"（仅在任务本身还不是终态时回写）。 */
    private suspend fun maybeAutoCompleteTask(taskId: Long, all: List<com.zhixing.data.entity.SubprojectEntity>, changedId: Long, newStatus: String) {
        val task = taskDao.getTaskById(taskId) ?: return
        val after = all.map { if (it.id == changedId) it.copy(status = newStatus) else it }
        if (task.status !in TERMINAL && TaskStatus.resolve(after.map { it.status }) == "已完成") {
            taskDao.updateTaskStatus(taskId, "已完成")
        }
    }

    private val TERMINAL = setOf("已完成", "已放弃")
}

class WeekScheduleViewModelFactory(
    private val weekDates: List<String>,
    private val scheduleDao: ScheduleDao,
    private val subprojectDao: SubprojectDao,
    private val taskDao: TaskDao,
    private val currentTimeProvider: () -> Int = { currentMinutesOfDay() },
    private val todayProvider: () -> String = { today() },
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WeekScheduleViewModel(weekDates, scheduleDao, subprojectDao, taskDao, currentTimeProvider, todayProvider) as T
    }
}

private fun currentMinutesOfDay(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}

private fun today(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
