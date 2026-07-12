package com.zhixing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
 * 任务标题（含描述）+ AI 拆解入口 + 子项目列表。
 * 拆解按钮与标题同行——标题占左、按钮靠右，"拆解该任务"动作紧邻任务本体，
 * 语义直接且不额外占用纵向空间。加载中指示器在按钮左侧，错误提示在标题行下方。
 * 子项目操作（排期/完成/放弃）已移至日程栏，任务栏只负责展示。
 */
@Composable
fun TaskDetailContent(
    taskTitle: String,
    subprojects: List<SubprojectEntity>,
    onSubprojectClick: (Long) -> Unit = {},
    taskDescription: String? = null,
    // AI 拆解入口：放在标题之后、子项目列表之前，作为"针对该任务的拆解动作"。
    decomposing: Boolean = false,
    decomposeError: String? = null,
    onDecompose: () -> Unit = {},
) {
    val spacing = LocalZhixingSpacing.current
    Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.lg)) {
        // 任务标题 + AI 拆解按钮同行：标题左（weight 占用剩余空间）、按钮右。
        // "拆解该任务"动作紧邻任务本体，语义直接、不过度抢占纵向空间。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = taskTitle,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(end = spacing.sm),
            )
            if (decomposing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).padding(end = spacing.sm).testTag("DecomposeLoadingIndicator"),
                    strokeWidth = 2.dp,
                )
            }
            Button(
                onClick = onDecompose,
                enabled = !decomposing,
                modifier = Modifier.testTag("DecomposeButton"),
            ) {
                Text("AI 拆解")
            }
        }

        if (decomposeError != null) {
            Text(
                text = decomposeError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = spacing.xs).testTag("DecomposeErrorText"),
            )
        }

        if (taskDescription != null) {
            Text(
                text = taskDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.md),
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

