package com.zhixing.data

/**
 * AI 拆解返回的单个子项目。
 *
 * 与 [com.zhixing.data.entity.SubprojectEntity] 是多对一关系：
 * 拆解结果写入 subprojects 表时，estimatedDuration 直接拷贝。
 */
data class DecompositionResult(
    val title: String,
    val estimatedDuration: Int,
)
