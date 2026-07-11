package com.zhixing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zhixing.ui.components.TimelineDot
import com.zhixing.ui.components.parseTaskStatus

/**
 * 回顾详情页的"完成时间线"进度图。
 *
 * 顶部：整体进度数字 + 线性进度条。
 * 主体：每个子项目一行（icon + 标题 + 完成日期），
 *       终态按 completedAt 升序，未完成排在后显示"—"。
 */
@Composable
fun ReviewProgressChart(result: TimelineResult, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 顶部整体进度
        val fraction = if (result.totalCount > 0) {
            result.completedCount.toFloat() / result.totalCount
        } else 0f

        Text(
            text = "已完成 ${result.completedCount}/${result.totalCount}",
            style = MaterialTheme.typography.titleMedium,
        )
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small)
                .testTag("ProgressBar"),
        )

        // 时间线列表
        result.items.forEach { item ->
            TimelineRow(item = item)
        }
    }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 状态图标：带语义 contentDescription 的圆点（已完成/已放弃/待办），
        // 取代纯 unicode 字符（屏幕阅读器可读出状态，满足 WCAG 1.1.1）。
        TimelineDot(status = parseTaskStatus(item.status))

        // 标题
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )

        // 完成日期（未完成显示"—"）
        Text(
            text = item.completedDate ?: "—",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
