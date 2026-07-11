package com.zhixing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zhixing.data.ai.SettingsStore
import com.zhixing.data.ai.SettingsStoreFactory
import com.zhixing.ui.components.ZhixingTopAppBar
import com.zhixing.ui.theme.LocalZhixingSpacing

/**
 * "AI 拆解设置"页。
 *
 * 用户填写后端代理的 base URL / API token / 模型名，保存到 [SettingsStore]。
 * 校验由 [SettingsViewModel.save] 抛 IllegalArgumentException，本页捕获后
 * 展示错误提示。
 *
 * [settingsStore] 可选注入，便于 instrumented test 使用纯内存 fake。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    onBack: () -> Unit,
    settingsStore: SettingsStore? = null,
) {
    val context = LocalContext.current
    val store = settingsStore ?: remember { SettingsStoreFactory.create(context) }
    val vm = remember { SettingsViewModel(store) }
    val baseUrl by vm.baseUrl.collectAsState()
    val token by vm.token.collectAsState()
    val model by vm.model.collectAsState()

    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            ZhixingTopAppBar(
                title = "AI 拆解设置",
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(LocalZhixingSpacing.current.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(LocalZhixingSpacing.current.lg),
        ) {
            Text(
                text = "后端代理配置",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "配置自建后端代理的地址、API 令牌与模型名。配置保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    vm.setBaseUrl(it)
                    errorMessage = null
                },
                label = { Text("Base URL") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("SettingsBaseUrlInput"),
            )
            OutlinedTextField(
                value = token,
                onValueChange = {
                    vm.setToken(it)
                    errorMessage = null
                },
                label = { Text("API Token") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("SettingsTokenInput"),
            )
            OutlinedTextField(
                value = model,
                onValueChange = {
                    vm.setModel(it)
                    errorMessage = null
                },
                label = { Text("模型名") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("SettingsModelInput"),
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("SettingsErrorText"),
                )
            }

            Button(
                onClick = {
                    try {
                        vm.save(baseUrl, token, model)
                        errorMessage = null
                    } catch (e: IllegalArgumentException) {
                        errorMessage = e.message
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("SettingsSaveButton"),
            ) {
                Text("保存")
            }
        }
    }
}
