package com.zhixing.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zhixing.ui.theme.LocalZhixingSpacing

/**
 * 弹窗「取消」按钮（置左的次要操作）。
 *
 * Material3 TextButton 默认最小高度 48dp，满足触摸目标规范。
 */
@Composable
fun DismissButton(
    onClick: () -> Unit,
    label: String = "取消",
) {
    TextButton(onClick = onClick) { Text(label) }
}

/**
 * 弹窗「确认」按钮（置右的主操作）。
 *
 * 使用填充式 [Button] 强调主操作，与次要的 [DismissButton] 形成层级。
 */
@Composable
fun ConfirmButton(
    onClick: () -> Unit,
    label: String = "确认",
    enabled: Boolean = true,
) {
    Button(onClick = onClick, enabled = enabled) { Text(label) }
}

/**
 * 通用单字段输入弹窗（创建任务 / 创建子项目 共用）。
 *
 * 与手写 AlertDialog 的差异：
 *   - 字段与按钮均使用统一 token 间距（[LocalZhixingSpacing]）。
 *   - 底部按钮使用 [DismissButton] / [ConfirmButton]，主操作居右且为填充样式。
 *
 * 通过 [fieldTag] 暴露 testTag，便于 instrumented test 断言。
 */
@Composable
fun ZhixingInputDialog(
    title: String,
    label: String,
    fieldTag: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(fieldTag),
            )
        },
        confirmButton = {
            ConfirmButton(onClick = { onConfirm(value) })
        },
        dismissButton = {
            DismissButton(onClick = onDismiss)
        },
    )
}
