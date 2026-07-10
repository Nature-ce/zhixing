package com.zhixing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * 空状态欢迎页。第一次打开 app（无任何数据）时展示。
 *
 * CONTEXT.md: 欢迎/价值主张文案 + 醒目的"创建第一个任务"按钮。
 */
@Composable
fun EmptyState(onCreateFirstTask: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "把大任务拆成小项目，一步步完成",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag("EmptyStateTitle"),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onCreateFirstTask) {
            Text("创建第一个任务")
        }
    }
}
