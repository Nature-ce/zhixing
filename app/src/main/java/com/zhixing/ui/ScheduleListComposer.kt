package com.zhixing.ui

import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity

/**
 * 日程展示项：排期实体 + 子项目标题（用于 UI 展示）。
 */
data class ScheduleItem(
    val id: Long,
    val subprojectId: Long = 0,
    val subprojectTitle: String,
    val startTime: Int,
    val endTime: Int,
    val subprojectStatus: String = "",
    val isOverdue: Boolean = false,
    val date: String = "",
    // 所属任务的 id，供预览层做同任务冲突判断（无需穿透 subprojects 列表）。
    val taskId: Long = 0L,
)

/**
 * 日程列表组装器。纯逻辑，零 Android 依赖。
 *
 * 把 ScheduleEntity 列表与 SubprojectEntity 列表 join，
 * 输出按 startTime 升序排列的 ScheduleItem。
 *
 * 逾期判定：排期日期 < today，或（日期 == today 且 endTime < currentTime）。
 */
object ScheduleListComposer {

    fun assemble(
        scheduleItems: List<ScheduleEntity>,
        subprojects: List<SubprojectEntity>,
        currentTime: Int = Int.MAX_VALUE,
        today: String = "",
    ): List<ScheduleItem> {
        val subprojectById = subprojects.associateBy { it.id }
        return scheduleItems
            .sortedBy { it.startTime }
            .map { entity ->
                val sub = subprojectById[entity.subprojectId]
                val overdue = entity.date < today ||
                    (entity.date == today && entity.endTime < currentTime)
                ScheduleItem(
                    id = entity.id,
                    subprojectId = entity.subprojectId,
                    subprojectTitle = sub?.title ?: "",
                    startTime = entity.startTime,
                    endTime = entity.endTime,
                    subprojectStatus = sub?.status ?: "",
                    isOverdue = overdue,
                    date = entity.date,
                    taskId = sub?.taskId ?: 0L,
                )
            }
    }
}
