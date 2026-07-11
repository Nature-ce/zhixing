package com.zhixing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zhixing.ui.components.ConfirmButton
import com.zhixing.ui.components.DismissButton

/**
 * 编辑任务信息对话框。
 *
 * 编辑标题 + 描述，确认后回调。标题不可为空。
 * 底部使用 [DismissConfirmFooter] 统一按钮样式。
 */
@Composable
fun EditTaskDialog(
    initialTitle: String,
    initialDescription: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text("编辑任务", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("EditTaskTitleInput"),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("EditTaskDescriptionInput"),
                    minLines = 3,
                )
            }
        },
        confirmButton = {
            ConfirmButton(
                onClick = { onConfirm(title, description) },
                enabled = title.isNotBlank(),
            )
        },
        dismissButton = {
            DismissButton(onClick = onDismiss)
        },
    )
}
