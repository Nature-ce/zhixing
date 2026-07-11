package com.zhixing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.ui.components.SectionHeader
import com.zhixing.ui.components.StatusChip
import com.zhixing.ui.components.ZhixingCard
import com.zhixing.ui.components.parseTaskStatus
import com.zhixing.ui.theme.LocalZhixingSpacing

/**
 * 任务详情内容区。
 *
 * 任务标题（含描述）+ 子项目列表。子项目操作（排期/完成/放弃）已移至日程栏，
 * 任务栏只负责展示，故子项目行当前为纯展示；点击事件留作扩展口。
 */
@Composable
fun TaskDetailContent(
    taskTitle: String,
    subprojects: List<SubprojectEntity>,
    onSubprojectClick: (Long) -> Unit = {},
    taskDescription: String? = null,
) {
    val spacing = LocalZhixingSpacing.current
    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg)) {
        Text(
            text = taskTitle,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = if (taskDescription != null) spacing.xs else spacing.md),
        )
        if (taskDescription != null) {
            Text(
                text = taskDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = spacing.lg),
            )
        }

        if (subprojects.isNotEmpty()) {
            SectionHeader(title = "子项目 ${subprojects.size}")
            Spacer(modifier = Modifier.height(spacing.sm))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
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
    val spacing = LocalZhixingSpacing.current
    ZhixingCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("SubprojectRow-${subproject.id}"),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = subproject.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .testTag(subproject.title),
            )
            StatusChip(
                status = parseTaskStatus(subproject.status),
                modifier = Modifier.testTag("SubprojectStatus-${subproject.id}"),
            )
        }
        if (subproject.estimatedDuration != null && subproject.estimatedDuration > 0) {
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                text = "预估 ${subproject.estimatedDuration} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
