package com.zhixing.ai

import com.zhixing.data.DecompositionService
import com.zhixing.data.ai.RemoteDecompositionService
import com.zhixing.data.ai.SettingsStore

/**
 * 生产侧装配点：把 [SettingsStore] 配置桥接到 [DecompositionService] 实例。
 *
 * 每次调用都基于 store 当前值新建服务——配置可能在"设置"页被用户改动，
 * 不应在内存中缓存过期的 Retrofit/OkHttp 实例。
 */
object ServiceProvider {

    /**
     * 基于 store 当前值构建拆解服务。
     *
     * 负责把分散的 baseUrl/token/model 汇聚成 [DecompositionService]，
     * 是 ViewModel 与网络实现之间的唯一连线。
     */
    fun decomposition(store: SettingsStore): DecompositionService =
        RemoteDecompositionService.create(
            baseUrl = store.getBaseUrl(),
            token = store.getToken(),
            model = store.getModel(),
        )
}
