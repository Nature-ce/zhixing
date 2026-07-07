package com.zhixing.data

/**
 * 子项目状态流转。纯领域逻辑，零 Android 依赖。
 *
 * 允许的流转:
 *   - backlog     -> 已排期 / 已完成 / 已放弃
 *   - 已排期      -> 已完成 / 已放弃
 *   - 已完成      -> 已排期（误操作撤销）
 *   - 已放弃      -> (终态，不可流转)
 */
object SubprojectState {

    private val ALLOWED: Map<String, Set<String>> = mapOf(
        "backlog" to setOf("已排期", "已完成", "已放弃"),
        "已排期" to setOf("已完成", "已放弃"),
        "已完成" to setOf("已排期"),
        "已放弃" to emptySet(),
    )

    fun transition(from: String, to: String): String {
        val allowed = ALLOWED[from]
            ?: throw IllegalArgumentException("Unknown from state: $from")
        if (to !in allowed) {
            throw IllegalArgumentException("Cannot transition from '$from' to '$to'")
        }
        return to
    }
}
