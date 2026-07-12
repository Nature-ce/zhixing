package com.zhixing.ui

import android.content.Context
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.zhixing.data.ai.SettingsStore
import com.zhixing.data.ai.SettingsStoreFactory
import com.zhixing.ui.theme.ZhixingTheme
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
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

    /**
     * TDD RED→GREEN：未保存过任何配置时，输入框应为空（无预填默认值）。
     *
     * 走完整生产路径 SettingsStoreFactory.create(context)（注入 BuildConfig 默认值），
     * 行为契约为"出厂时空字符串"，避免用户需要手动删掉假占位输入。
     */
    @Test
    fun inputs_empty_whenNothingSaved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // 确保出厂态：清空持久化 prefs
        context.getSharedPreferences("zhixing_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = SettingsStoreFactory.create(context)

        composeRule.setContent {
            ZhixingTheme {
                SettingsPage(onBack = {}, settingsStore = store)
            }
        }

        // 行为契约：未保存时 EditableText 为空（Text 同时含 label，需一并匹配）。
        // 预填时 EditableText 为假占位文本，此断言即失败。
        composeRule.onNodeWithTag("SettingsBaseUrlInput").assertTextEquals("Base URL", "")
        composeRule.onNodeWithTag("SettingsTokenInput").assertTextEquals("API Token", "")
        composeRule.onNodeWithTag("SettingsModelInput").assertTextEquals("模型名", "")
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
