package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 * 排期动作与任务自动完成逻辑见基类 [ScheduleViewModel]。
 */
class WeekScheduleViewModel(
    private val weekDates: List<String>,
    scheduleDao: ScheduleDao,
    subprojectDao: SubprojectDao,
    taskDao: TaskDao,
    private val currentTimeProvider: () -> Int = { currentMinutesOfDay() },
    private val todayProvider: () -> String = { today() },
) : ScheduleViewModel(scheduleDao, subprojectDao, taskDao) {

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
