package com.zhixing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zhixing.ui.components.SectionHeader
import com.zhixing.ui.components.StatusChip
import com.zhixing.ui.components.ZhixingCard
import com.zhixing.ui.components.parseTaskStatus
import com.zhixing.ui.theme.LocalZhixingSpacing

/**
 * 任务列表视图。
 *
 * 空列表 → EmptyState 欢迎页
 * 有卡片时 → 任务计数徽章 + 卡片列表（标题 + StatusChip + 进度条）
 */
@Composable
fun ListView(
    taskItems: List<TaskListItem>,
    onCreateFirstTask: () -> Unit,
    onTaskClick: (Long) -> Unit = {},
    hasAnyTask: Boolean = false,
) {
    val spacing = LocalZhixingSpacing.current

    if (taskItems.isEmpty()) {
        if (hasAnyTask) {
            // 任务都完成了/已放弃，列表没进行中任务
            Column(
                modifier = Modifier.fillMaxSize().padding(spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "没有进行中的任务",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("EmptyActiveTasksTitle"),
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = "去回顾页看看",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("EmptyActiveTasksHint"),
                )
                Spacer(modifier = Modifier.height(spacing.xl))
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
            contentPadding = PaddingValues(
                horizontal = spacing.lg,
                vertical = spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "list-header") {
                SectionHeader(title = "任务")
                Text(
                    text = "${taskItems.size} 个进行中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
            }
            items(taskItems, key = { it.id }) { item ->
                TaskCard(item = item, onClick = { onTaskClick(item.id) })
            }
        }
    }
}

@Composable
private fun TaskCard(item: TaskListItem, onClick: () -> Unit = {}) {
    val spacing = LocalZhixingSpacing.current
    val fraction = if (item.totalCount > 0) {
        item.completedCount.toFloat() / item.totalCount
    } else 0f
    ZhixingCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("TaskCard-${item.id}"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .testTag("TaskTitle-${item.id}"),
            )
            StatusChip(status = parseTaskStatus(item.statusLabel))
        }
        Spacer(modifier = Modifier.height(spacing.md))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(LocalZhixingSpacing.current.xs)),
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = "${item.completedCount}/${item.totalCount} 个子项目已完成",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
