package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity
import java.util.Calendar
import java.util.TimeZone

/**
 * 时间线图标类型，按子项目状态区分。
 */
enum class TimelineIcon {
    CHECK,      // 已完成
    ABANDON,    // 已放弃
    PENDING,    // 未完成（已排期 / backlog）
}

/**
 * 时间线中单个子项目的展示模型。
 */
data class TimelineItem(
    val id: Long,
    val title: String,
    val icon: TimelineIcon,
    val status: String,
    val completedDate: String?,
)

/**
 * 时间线整体展示模型。
 */
data class TimelineResult(
    val completedCount: Int,
    val totalCount: Int,
    val items: List<TimelineItem>,
)

/**
 * 时间线组装器。纯逻辑，零 Android 依赖。
 *
 * 排序规则：终态子项目（有 completedAt）按 completedAt 升序排前面，
 * 未完成的排在后面。用于回顾详情页的"完成时间线"。
 */
object TimelineComposer {

    private val TERMINAL = setOf("已完成", "已放弃")

    fun assemble(subprojects: List<SubprojectEntity>): TimelineResult {
        val total = subprojects.size
        val completed = subprojects.count { it.status in TERMINAL }

        val items = subprojects
            .sortedWith(compareBy({ it.completedAt == null }, { it.completedAt ?: 0L }))
            .map { sub ->
                val icon = when (sub.status) {
                    "已完成" -> TimelineIcon.CHECK
                    "已放弃" -> TimelineIcon.ABANDON
                    else -> TimelineIcon.PENDING
                }
                TimelineItem(
                    id = sub.id,
                    title = sub.title,
                    icon = icon,
                    status = sub.status,
                    completedDate = sub.completedAt?.let { formatCompletedDate(it) },
                )
            }

        return TimelineResult(
            completedCount = completed,
            totalCount = total,
            items = items,
        )
    }
}

/**
 * 将 epoch 毫秒按 UTC 格式化为 "yyyy-MM-dd"。
 * 抽为纯函数便于 JVM 单元测试。
 */
fun formatCompletedDate(millis: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = millis
    return "%04d-%02d-%02d".format(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH),
    )
}
