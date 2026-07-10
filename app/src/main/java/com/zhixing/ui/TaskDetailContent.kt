package com.zhixing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zhixing.data.entity.SubprojectEntity

/**
 * 任务详情内容区。
 *
 * 展示任务标题 + 子项目列表（标题 + 状态标签）。
 * 点击子项目行 → onSubprojectClick(id)，由调用方决定状态流转逻辑。
 */
@Composable
fun TaskDetailContent(
    taskTitle: String,
    subprojects: List<SubprojectEntity>,
    onSubprojectClick: (Long) -> Unit = {},
    taskDescription: String? = null,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = taskTitle,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = if (taskDescription != null) 4.dp else 16.dp),
        )
        if (taskDescription != null) {
            Text(
                text = taskDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 子项目操作（排期/完成/放弃）已移至日程栏，任务栏只负责展示与创建。
            items(subprojects, key = { it.id }) { sub ->
                SubprojectRow(
                    subproject = sub,
                    onClick = { onSubprojectClick(sub.id) },
                )
            }
        }
    }
}

@Composable
private fun SubprojectRow(
    subproject: SubprojectEntity,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("SubprojectRow-${subproject.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .semantics(mergeDescendants = false) {},
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = subproject.title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subproject.status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("SubprojectStatus-${subproject.id}"),
            )
        }
    }
}
