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
 * 创建任务对话框。
 *
 * 用户输入标题 → 确认/取消。
 * 标题状态由 composable 自己持有（局部 UI 状态），不污染 ViewModel。
 */
@Composable
fun CreateTaskDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建任务") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("任务标题") },
                singleLine = true,
                modifier = Modifier.testTag("TaskTitleInput"),
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
