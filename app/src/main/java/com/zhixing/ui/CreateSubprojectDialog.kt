package com.zhixing.ui

import androidx.compose.material3.AlertDialog
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

/**
 * 添加子项目对话框。
 *
 * 用户输入标题 → 确认/取消。
 * 确认后调用方插入 backlog 状态子项目。
 */
@Composable
fun CreateSubprojectDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加子项目") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("子项目标题") },
                singleLine = true,
                modifier = Modifier.testTag("SubprojectTitleInput"),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
