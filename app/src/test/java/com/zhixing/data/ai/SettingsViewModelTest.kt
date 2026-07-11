package com.zhixing.data.ai

import com.zhixing.data.ai.SettingsStore
import com.zhixing.ui.SettingsViewModel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

/**
 * JVM TDD: SettingsViewModel 的保存 + 校验契约。
 *
 * 验证：
 *   - save() 把字段写入 store
 *   - baseUrl 为空 → 抛 IllegalArgumentException
 *   - baseUrl 不含 "://" → 抛 IllegalArgumentException（简单格式校验）
 *   - token 为空 → 抛 IllegalArgumentException
 */
class SettingsViewModelTest {

    /** 纯内存假实现，额外记录写入值以便断言。 */
    class FakeSettingsStore(
        private val defaults: Pair<String, String>,
        private val defaultModel: String = "deepseek-chat",
    ) : SettingsStore {
        val saved = mutableMapOf<String, String>()
        override fun getBaseUrl(): String = saved[SettingsStore.KEY_BASE_URL] ?: defaults.first
        override fun getToken(): String = saved[SettingsStore.KEY_TOKEN] ?: defaults.second
        override fun getModel(): String = saved[SettingsStore.KEY_MODEL] ?: defaultModel
        override fun saveBaseUrl(url: String) { saved[SettingsStore.KEY_BASE_URL] = url }
        override fun saveToken(token: String) { saved[SettingsStore.KEY_TOKEN] = token }
        override fun saveModel(model: String) { saved[SettingsStore.KEY_MODEL] = model }
        override fun clear() { saved.clear() }
    }

    private val defaults = "https://default.example.com/" to "default-token"
    private val store = FakeSettingsStore(defaults)
    private val vm = SettingsViewModel(store)

    @Test
    fun save_writesToStore() {
        vm.save("https://custom.example.com/", "custom-token")

        assertThat(store.saved[SettingsStore.KEY_BASE_URL]).isEqualTo("https://custom.example.com/")
        assertThat(store.saved[SettingsStore.KEY_TOKEN]).isEqualTo("custom-token")
    }

    @Test
    fun save_throws_whenBaseUrlBlank() {
        assertThatThrownBy { vm.save("   ", "token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("URL")
    }

    @Test
    fun save_throws_whenBaseUrlHasNoScheme() {
        assertThatThrownBy { vm.save("not-a-url", "token") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("://")
    }

    @Test
    fun save_writesModelToStore() {
        vm.save("https://custom.example.com/", "custom-token", "LongCat-Flash-Chat")

        assertThat(store.saved[SettingsStore.KEY_MODEL]).isEqualTo("LongCat-Flash-Chat")
    }

    @Test
    fun save_throws_whenTokenBlank() {
        assertThatThrownBy { vm.save("https://ok.example.com/", "") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Token")
    }
}
