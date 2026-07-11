package com.zhixing.data

/**
 * AI 拆解服务契约。
 *
 * 接口抽象让 JVM 测试可用纯内存 fake 验证 ViewModel 契约，无需 Android。
 *
 * 实现类（如 [com.zhixing.data.ai.RemoteDecompositionService]）走后端代理
 * （ADR-0001）：app 不直调大模型。
 */
interface DecompositionService {
    /**
     * 把 [taskTitle]（+ 可选 [taskDescription]）拆解为子项目列表。
     *
     * 任何失败抛 [DecompositionException]，UI 层捕获后展示错误提示。
     */
    suspend fun decompose(taskTitle: String, taskDescription: String?): List<DecompositionResult>
}
