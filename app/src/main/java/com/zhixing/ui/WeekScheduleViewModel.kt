package com.zhixing.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zhixing.data.ScheduleConflictDetector
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.ScheduleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
) : ViewModel() {

    private val _itemsByDate = MutableStateFlow<Map<String, List<ScheduleItem>>>(emptyMap())
    val itemsByDate: StateFlow<Map<String, List<ScheduleItem>>> = _itemsByDate.asStateFlow()

    // 本周 backlog 子项目：status=backlog 且本周 7 天都未排期。
    private val _backlogItems = MutableStateFlow<List<BacklogItem>>(emptyList())
    val backlogItems: StateFlow<List<BacklogItem>> = _backlogItems.asStateFlow()

    init {
        if (weekDates.isNotEmpty()) {
            val startDate = weekDates.first()
            val endDate = weekDates.last()
            viewModelScope.launch {
                combine(
                    scheduleDao.getScheduleItemsBetween(startDate, endDate),
                    subprojectDao.getAllSubprojects(),
                    taskDao.getAllTasks(),
                ) { scheduleItems, subprojects, tasks ->
                    val assembled = ScheduleListComposer.assemble(scheduleItems, subprojects)
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
}

class WeekScheduleViewModelFactory(
    private val weekDates: List<String>,
    private val scheduleDao: ScheduleDao,
    private val subprojectDao: SubprojectDao,
    private val taskDao: TaskDao,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return WeekScheduleViewModel(weekDates, scheduleDao, subprojectDao, taskDao) as T
    }
}
