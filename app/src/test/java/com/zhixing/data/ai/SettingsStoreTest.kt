package com.zhixing.data.ai

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * JVM TDD: SettingsStore 的读写契约。
 *
 * 用内联 fake（纯 Kotlin，无需 Android）验证：
 *   - 写入后读回
 *   - 未写入时退回默认值（默认值来自 BuildConfig，由调用方注入）
 *   - clear 后退回默认值
 */
class SettingsStoreTest {

    /** 纯内存假实现，用于 JVM 验证 SettingsStore 契约。 */
    class FakeSettingsStore(
        private val urlDefault: String,
        private val tokenDefault: String,
        private val modelDefault: String,
    ) : SettingsStore {
        private val map = mutableMapOf<String, String>()

        override fun getBaseUrl(): String = map[KEY_URL] ?: urlDefault
        override fun getToken(): String = map[KEY_TOKEN] ?: tokenDefault
        override fun getModel(): String = map[KEY_MODEL] ?: modelDefault
        override fun saveBaseUrl(url: String) { map[KEY_URL] = url }
        override fun saveToken(token: String) { map[KEY_TOKEN] = token }
        override fun saveModel(model: String) { map[KEY_MODEL] = model }
        override fun clear() { map.clear() }

        companion object {
            const val KEY_URL = "base_url"
            const val KEY_TOKEN = "api_token"
            const val KEY_MODEL = "model"
        }
    }

    private val store: SettingsStore = FakeSettingsStore(
        urlDefault = "https://default.example.com/",
        tokenDefault = "default-token",
        modelDefault = "deepseek-chat",
    )

    @Test
    fun load_returnsDefault_whenNothingSaved() {
        assertThat(store.getBaseUrl()).isEqualTo("https://default.example.com/")
        assertThat(store.getToken()).isEqualTo("default-token")
    }

    @Test
    fun saveThenLoad_returnsSavedValue() {
        store.saveBaseUrl("https://custom.example.com/")
        store.saveToken("custom-token")

        assertThat(store.getBaseUrl()).isEqualTo("https://custom.example.com/")
        assertThat(store.getToken()).isEqualTo("custom-token")
    }

    @Test
    fun clear_resetsToDefault() {
        store.saveBaseUrl("https://custom.example.com/")
        store.saveToken("custom-token")
        store.clear()

        assertThat(store.getBaseUrl()).isEqualTo("https://default.example.com/")
        assertThat(store.getToken()).isEqualTo("default-token")
    }

    @Test
    fun model_loadsDefault_whenNothingSaved() {
        assertThat(store.getModel()).isEqualTo("deepseek-chat")
    }

    @Test
    fun model_saveThenLoad_returnsSavedValue() {
        store.saveModel("LongCat-Flash-Chat")
        assertThat(store.getModel()).isEqualTo("LongCat-Flash-Chat")
    }

    @Test
    fun model_clear_resetsToDefault() {
        store.saveModel("LongCat-Flash-Chat")
        store.clear()
        assertThat(store.getModel()).isEqualTo("deepseek-chat")
    }
}
