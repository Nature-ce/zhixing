package com.zhixing.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.zhixing.data.ai.SettingsStore
import com.zhixing.ui.theme.ZhixingTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

/**
 * UI TDD (createComposeRule): SettingsPage 渲染 + 保存 + 校验。
 *
 * 用纯内存 FakeSettingsStore 注入验证：
 *   - 字段显示默认值
 *   - 输入并保存 → 写入 store
 *   - baseUrl 为空保存 → 展示错误
 */
class SettingsPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    class FakeSettingsStore(
        private val defaultBaseUrl: String = "",
        private val defaultToken: String = "",
        private val defaultModel: String = "",
    ) : SettingsStore {
        val map = mutableMapOf<String, String>()
        override fun getBaseUrl() = map[SettingsStore.KEY_BASE_URL] ?: defaultBaseUrl
        override fun getToken() = map[SettingsStore.KEY_TOKEN] ?: defaultToken
        override fun getModel() = map[SettingsStore.KEY_MODEL] ?: defaultModel
        override fun saveBaseUrl(url: String) { map[SettingsStore.KEY_BASE_URL] = url }
        override fun saveToken(token: String) { map[SettingsStore.KEY_TOKEN] = token }
        override fun saveModel(model: String) { map[SettingsStore.KEY_MODEL] = model }
        override fun clear() { map.clear() }
    }

    @Test
    fun settings_shows_defaultValues_and_save_writesToStore() {
        val store = FakeSettingsStore(
            defaultBaseUrl = "https://default.example.com/",
            defaultToken = "default-token",
            defaultModel = "deepseek-chat",
        )
        composeRule.setContent {
            ZhixingTheme {
                SettingsPage(onBack = {}, settingsStore = store)
            }
        }

        // 默认值渲染到字段
        composeRule.onNodeWithTag("SettingsBaseUrlInput").assertTextContains("https://default.example.com/")
        composeRule.onNodeWithTag("SettingsTokenInput").assertTextContains("default-token")
        composeRule.onNodeWithTag("SettingsModelInput").assertTextContains("deepseek-chat")

        // 输入新值
        composeRule.onNodeWithTag("SettingsBaseUrlInput").performTextClearance()
        composeRule.onNodeWithTag("SettingsBaseUrlInput").performTextInput("https://custom.example.com/")
        composeRule.onNodeWithTag("SettingsTokenInput").performTextClearance()
        composeRule.onNodeWithTag("SettingsTokenInput").performTextInput("custom-token")
        composeRule.onNodeWithTag("SettingsModelInput").performTextClearance()
        composeRule.onNodeWithTag("SettingsModelInput").performTextInput("LongCat-Flash-Chat")

        composeRule.onNodeWithTag("SettingsSaveButton").performClick()

        assertThat(store.map[SettingsStore.KEY_BASE_URL]).isEqualTo("https://custom.example.com/")
        assertThat(store.map[SettingsStore.KEY_TOKEN]).isEqualTo("custom-token")
        assertThat(store.map[SettingsStore.KEY_MODEL]).isEqualTo("LongCat-Flash-Chat")
    }

    @Test
    fun save_with_empty_baseUrl_shows_error() {
        val store = FakeSettingsStore(
            defaultBaseUrl = "https://default.example.com/",
            defaultToken = "default-token",
            defaultModel = "deepseek-chat",
        )
        composeRule.setContent {
            ZhixingTheme {
                SettingsPage(onBack = {}, settingsStore = store)
            }
        }

        composeRule.onNodeWithTag("SettingsBaseUrlInput").performTextClearance()
        composeRule.onNodeWithTag("SettingsSaveButton").performClick()

        composeRule.onNodeWithTag("SettingsErrorText").assertIsDisplayed()
    }
}
