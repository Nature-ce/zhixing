package com.zhixing.data

import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity

/**
 * 排期时间冲突检测。纯领域逻辑，零 Android 依赖。
 *
 * CONTEXT.md 规则:
 *   - 把子项目拖放到已被占用的时间格子（同一任务内）→ 冲突
 *   - 把子项目拖放到已被占用的时间格子（跨任务）→ 允许重叠
 */
object ScheduleConflictDetector {

    /**
     * 判断为 [subprojectId] 在 [date] 的 [startTime, endTime) 排期
     * 是否会与同任务的已排期项时间重叠。
     */
    fun hasConflict(
        subprojectId: Long,
        date: String,
        startTime: Int,
        endTime: Int,
        existingSchedules: List<ScheduleEntity>,
        subprojects: List<SubprojectEntity>,
    ): Boolean {
        val subprojectById = subprojects.associateBy { it.id }
        val targetTask = subprojectById[subprojectId]?.taskId ?: return false

        return existingSchedules.any { existing ->
            if (existing.subprojectId == subprojectId) return@any false
            if (existing.date != date) return@any false
            val existingTask = subprojectById[existing.subprojectId]?.taskId ?: return@any false
            if (existingTask != targetTask) return@any false
            // 时间重叠：半开区间 [start, endTime)
            existing.startTime < endTime && existing.endTime > startTime
        }
    }
}
