package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.SubprojectEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 日视图 ViewModel。
 *
 * 按日期加载排期项，并与子项目 join 得到展示数据。
 * 排期动作与任务自动完成逻辑见基类 [ScheduleViewModel]。
 */
class DayScheduleViewModel(
    private val date: String,
    taskDao: TaskDao,
    scheduleDao: ScheduleDao,
    subprojectDao: SubprojectDao,
    private val currentTimeProvider: () -> Int = { currentMinutesOfDay() },
) : ScheduleViewModel(scheduleDao, subprojectDao, taskDao) {

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
