package com.zhixing.data.ai

import android.content.Context
import android.content.SharedPreferences
import com.zhixing.BuildConfig

/**
 * AI 拆解后端代理配置（base URL + API token + 模型名）的持久化。
 *
 * 用户可在"设置"页填写；未填写时退回 [BuildConfig] 默认值（占位）。
 * 接口抽象让 JVM 测试可用纯内存 fake 验证契约，无需 Android。
 */
interface SettingsStore {
    fun getBaseUrl(): String
    fun getToken(): String
    fun getModel(): String
    fun saveBaseUrl(url: String)
    fun saveToken(token: String)
    fun saveModel(model: String)
    fun clear()

    companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_TOKEN = "api_token"
        const val KEY_MODEL = "model"
    }
}

/**
 * 基于 [SharedPreferences] 的实现；无值时退回 [BuildConfig] 默认值。
 *
 * 生产侧通过 [SettingsStoreFactory.create] 构造；默认值注入让实现可在 JVM
 * 用 fake 完整测试（默认值行为由 Store 封装，UI / VM 不必感知）。
 */
class SharedPrefsSettingsStore(
    private val prefs: SharedPreferences,
    private val defaultBaseUrl: String,
    private val defaultToken: String,
    private val defaultModel: String,
) : SettingsStore {

    override fun getBaseUrl(): String =
        prefs.getString(SettingsStore.KEY_BASE_URL, defaultBaseUrl) ?: defaultBaseUrl

    override fun getToken(): String =
        prefs.getString(SettingsStore.KEY_TOKEN, defaultToken) ?: defaultToken

    override fun getModel(): String =
        prefs.getString(SettingsStore.KEY_MODEL, defaultModel) ?: defaultModel

    override fun saveBaseUrl(url: String) {
        prefs.edit().putString(SettingsStore.KEY_BASE_URL, url).apply()
    }

    override fun saveToken(token: String) {
        prefs.edit().putString(SettingsStore.KEY_TOKEN, token).apply()
    }

    override fun saveModel(model: String) {
        prefs.edit().putString(SettingsStore.KEY_MODEL, model).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

/** 生产侧装配点：用 app Context 构造 [SharedPrefsSettingsStore]。 */
object SettingsStoreFactory {
    private const val PREFS_NAME = "zhixing_settings"

    fun create(context: Context): SettingsStore {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return SharedPrefsSettingsStore(
            prefs = prefs,
            defaultBaseUrl = BuildConfig.DECOMPOSE_BASE_URL,
            defaultToken = BuildConfig.DECOMPOSE_TOKEN,
            defaultModel = BuildConfig.DECOMPOSE_MODEL,
        )
    }
}
