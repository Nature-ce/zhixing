package com.zhixing.ui

import androidx.lifecycle.ViewModel
import com.zhixing.data.ai.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "设置"页 ViewModel：加载 [SettingsStore] 当前配置、校验并保存。
 *
 * 校验失败抛 [IllegalArgumentException]（与 SubprojectState.transition 一致），
 * UI 层捕获后展示错误提示。
 */
class SettingsViewModel(
    private val store: SettingsStore,
) : ViewModel() {

    private val _baseUrl = MutableStateFlow(store.getBaseUrl())
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    private val _token = MutableStateFlow(store.getToken())
    val token: StateFlow<String> = _token.asStateFlow()

    private val _model = MutableStateFlow(store.getModel())
    val model: StateFlow<String> = _model.asStateFlow()

    /** 更新输入（不保存）。 */
    fun setBaseUrl(value: String) { _baseUrl.value = value }
    fun setToken(value: String) { _token.value = value }
    fun setModel(value: String) { _model.value = value }

    /**
     * 校验并保存；校验失败抛 IllegalArgumentException。
     * model 为可选字段（空时退回 BuildConfig 默认值）。
     */
    fun save(baseUrl: String, token: String, model: String = _model.value) {
        val trimmedUrl = baseUrl.trim()
        val trimmedToken = token.trim()
        val trimmedModel = model.trim()
        require(trimmedUrl.isNotBlank()) { "Base URL 不能为空" }
        require(trimmedUrl.contains("://")) { "Base URL 格式不正确，需包含 ://（如 https://...）" }
        require(trimmedToken.isNotBlank()) { "API Token 不能为空" }
        require(trimmedModel.isNotBlank()) { "模型名不能为空" }
        store.saveBaseUrl(trimmedUrl)
        store.saveToken(trimmedToken)
        store.saveModel(trimmedModel)
    }
}
