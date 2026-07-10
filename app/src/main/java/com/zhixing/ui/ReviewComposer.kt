package com.zhixing.ui

import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.data.entity.TaskEntity

/**
 * 回顾解锁判定（纯逻辑，零 Android 依赖）。
 *
 * 验收标准：任务进行过程中"写回顾"入口锁定，完成后解锁。
 * 解锁条件 = task 已被放弃，或至少有一个子项目且全部子项目都已终态。
 */
object ReviewComposer {

    private val TERMINAL = setOf("已完成", "已放弃")

    /** 该任务是否允许撰写/查看回顾。 */
    fun isReviewUnlocked(task: TaskEntity, subprojects: List<SubprojectEntity>): Boolean {
        if (task.status == "已放弃") return true
        return subprojects.isNotEmpty() && subprojects.all { it.status in TERMINAL }
    }
}
