package com.zhixing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * 任务列表视图。
 *
 * 空列表 → EmptyState 欢迎页
 * 有数据 → 卡片列表（标题 + 状态标签 + 进度数字）
 */
@Composable
fun ListView(
    taskItems: List<TaskListItem>,
    onCreateFirstTask: () -> Unit,
    onTaskClick: (Long) -> Unit = {},
    hasAnyTask: Boolean = false,
) {
    if (taskItems.isEmpty()) {
        if (hasAnyTask) {
            // 任务都完成了/已放弃，列表没进行中任务
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "没有进行中的任务",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("EmptyActiveTasksTitle"),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "去回顾页看看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("EmptyActiveTasksHint"),
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onCreateFirstTask) {
                    Text("创建新任务")
                }
            }
        } else {
            EmptyState(onCreateFirstTask = onCreateFirstTask)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(taskItems, key = { it.id }) { item ->
                TaskCard(item = item, onClick = { onTaskClick(item.id) })
            }
        }
    }
}

@Composable
private fun TaskCard(item: TaskListItem, onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("TaskCard-${item.id}")
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("TaskTitle-${item.id}"),
            )
            Text(
                text = item.statusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${item.completedCount}/${item.totalCount}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
