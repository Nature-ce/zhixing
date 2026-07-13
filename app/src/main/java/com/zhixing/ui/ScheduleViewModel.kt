package com.zhixing.ui

import androidx.lifecycle.ViewModel
import com.zhixing.data.ScheduleConflictDetector
import com.zhixing.data.SubprojectState
import com.zhixing.data.TaskStatus
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import kotlinx.coroutines.flow.first

/**
 * 日 / 周视图 ViewModel 基类。
 *
 * 排期动作（schedule / complete / unschedule / abandon / update）与任务自动完成逻辑
 * 在两种视图间完全一致，仅构造参数（单日期 vs 日期列表）与 StateFlow 形态不同，
 * 故抽到此类以消除重复。
 *
 * 所有动作采用防御式写法：子项目不存在或状态流转非法时返回 false，不抛异常。
 */
abstract class ScheduleViewModel(
    protected val scheduleDao: ScheduleDao,
    protected val subprojectDao: SubprojectDao,
    protected val taskDao: TaskDao,
) : ViewModel() {

    protected val TERMINAL = setOf("已完成", "已放弃")

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
     * 重排已排项块：把子项目改到同天的新时段（时长由调用方保留）。
     *
     * 仅当目标时段与同任务其他子项目无冲突时才执行：清除原排期行 + 插入新排期行，
     * 子项目状态保持"已排期"不变。冲突则拒绝（返回 false，保持原位）。
     *
     * @return 是否成功重排
     */
    open suspend fun rescheduleSubproject(
        subprojectId: Long,
        date: String,
        startTime: Int,
        endTime: Int,
    ): Boolean {
        val current = subprojectDao.getAllSubprojects().first().firstOrNull { it.id == subprojectId }
            ?: return false
        // 仅允许重排已排期的子项目（与 unschedule 共享前置）。
        if (current.status != "已排期") return false
        val existing = scheduleDao.getScheduleItemsByDate(date).first()
        // 冲突检测排除自身原有行（ScheduleConflictDetector 已按 subprojectId 排除）。
        if (ScheduleConflictDetector.hasConflict(
                subprojectId = subprojectId,
                date = date,
                startTime = startTime,
                endTime = endTime,
                existingSchedules = existing,
                subprojects = subprojectDao.getAllSubprojects().first(),
            )
        ) {
            return false
        }
        scheduleDao.clearScheduleForSubproject(subprojectId)
        scheduleDao.insertScheduleItem(
            ScheduleEntity(
                subprojectId = subprojectId,
                date = date,
                startTime = startTime,
                endTime = endTime,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    /**
     * 标记子项目"已完成"。先验证存在性与流转合法性，再写状态 + 完成时间戳；
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
    protected suspend fun maybeAutoCompleteTask(
        taskId: Long,
        all: List<SubprojectEntity>,
        changedId: Long,
        newStatus: String,
    ) {
        val task = taskDao.getTaskById(taskId) ?: return
        val after = all.map { if (it.id == changedId) it.copy(status = newStatus) else it }
        if (task.status !in TERMINAL && TaskStatus.resolve(after.map { it.status }) == "已完成") {
            taskDao.updateTaskStatus(taskId, "已完成")
        }
    }
}
