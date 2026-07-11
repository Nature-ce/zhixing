package com.zhixing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhixing.ui.theme.LocalZhixingElevation
import com.zhixing.ui.theme.LocalZhixingRadii
import com.zhixing.ui.theme.LocalZhixingSpacing
import com.zhixing.ui.theme.LocalZhixingStatus
import com.zhixing.ui.theme.StatusDoneBg
import com.zhixing.ui.theme.StatusDoneFg

/**
 * 项目状态枚举。把分散在各处的字符串字面量（"进行中"/"已完成"/"已放弃"/"backlog"）
 * 收敛为此枚举，作为 [StatusChip] / 时间线 / 排期块的唯一输入。
 */
enum class TaskStatus { ACTIVE, DONE, ABANDON, BACKLOG }

/** 把数据库字符串解析为 [TaskStatus]。未知/空值视为 ACTIVE。 */
fun parseTaskStatus(raw: String?): TaskStatus = when (raw) {
    "已完成" -> TaskStatus.DONE
    "已放弃" -> TaskStatus.ABANDON
    "backlog" -> TaskStatus.BACKLOG
    else -> TaskStatus.ACTIVE
}

/** [TaskStatus] 的人类可读标签。 */
fun TaskStatus.label(): String = when (this) {
    TaskStatus.ACTIVE -> "进行中"
    TaskStatus.DONE -> "已完成"
    TaskStatus.ABANDON -> "已放弃"
    TaskStatus.BACKLOG -> "待排期"
}

/**
 * 状态芯片：统一的状态标签外观（进行中 / 已完成 / 已放弃 / 待排期）。
 *
 * 设计要点：
 *   - 用色块底色（背景色）+ 深色文字（前景色）表达状态，**同时文字写明状态名**，
 *     不依赖单一颜色传递信息（WCAG 1.4.1）。
 *   - 前景/背景配对取自 [com.zhixing.ui.theme.ZhixingStatusColors]，保证 ≥4.5:1。
 *   - 触摸目标 ≥ 48dp 高度（defaultMinSize）。
 *   - 合并子节点语义，让 TalkBack 一次性读出"已完成"而不是分开读"已""完成"。
 */
@Composable
fun StatusChip(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    StatusChipImpl(status = status, modifier = modifier, showLabel = true)
}

/**
 * 仅圆点、无文字的状态指示器（用于紧凑空间）。
 * [modifier] 供调用方注入 testTag 等。
 */
@Composable
fun StatusDot(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    StatusChipImpl(status = status, modifier = modifier, showLabel = false)
}

@Composable
private fun StatusChipImpl(
    status: TaskStatus,
    modifier: Modifier,
    showLabel: Boolean,
) {
    val palette = LocalZhixingStatus.current
    val (bg, fg) = when (status) {
        TaskStatus.ACTIVE -> palette.activeBg to palette.activeFg
        TaskStatus.DONE -> palette.doneBg to palette.doneFg
        TaskStatus.ABANDON -> palette.abandonBg to palette.abandonFg
        TaskStatus.BACKLOG -> palette.backlogBg to palette.backlogFg
    }
    val text = status.label()
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 24.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = text
            },
        contentAlignment = Alignment.Center,
    ) {
        if (showLabel) {
            Text(
                text = text,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * 时间线圆点（已完成 ✓ / 已放弃 ✕ / 待办 ○）。
 *
 * 用真实图标语义 + [contentDescription] 代替纯 unicode 字符（原实现用 "✓✕○"，
 * 屏幕阅读器读无意义字符），满足 WCAG 1.1.1 文本替代。
 */
@Composable
fun TimelineDot(
    status: TaskStatus,
    modifier: Modifier = Modifier,
) {
    val dot = resolveDot(status)
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(dot.bg)
            .semantics { contentDescription = dot.description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dot.glyph,
            color = dot.fg,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
    }
}

private data class Dot(val bg: Color, val fg: Color, val glyph: String, val description: String)

@Composable
private fun resolveDot(status: TaskStatus): Dot {
    val palette = LocalZhixingStatus.current
    return when (status) {
        TaskStatus.DONE -> Dot(palette.doneBg, palette.doneFg, "✓", "已完成")
        TaskStatus.ABANDON -> Dot(palette.abandonBg, palette.abandonFg, "✕", "已放弃")
        else -> Dot(palette.activeBg, palette.activeFg, "○", "待办")
    }
}

/**
 * 统一卡片容器。
 *
 * 取代各处各自拼 `Card { Column { padding(16.dp) ... } } }` 的散弹写法。
 *   - 圆角 / elevation / 内边距均取 token（[LocalZhixingRadii] / [LocalZhixingElevation]）。
 *   - 支持可选 onClick（交互式卡片），无 onClick 时退化为静态容器。
 */
@Composable
fun ZhixingCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(LocalZhixingSpacing.current.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val radii = LocalZhixingRadii.current
    val elevation = LocalZhixingElevation.current
    val shape = RoundedCornerShape(radii.md)
    if (onClick != null) {
        Card(
            modifier = modifier,
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.medium),
            onClick = onClick,
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            elevation = CardDefaults.cardElevation(defaultElevation = elevation.medium),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * 分组小标题（"待排期"、"后端代理配置"、分组头等复用）。
 *
 * 提供可选右侧 action slot（如折叠箭头、计数）。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LocalZhixingSpacing.current.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LocalZhixingSpacing.current.sm),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        action()
    }
    Spacer(modifier = Modifier.height(LocalZhixingSpacing.current.xs))
}

/**
 * 统一顶栏：整站在任务列表 / 设置 / 任务详情 三处复用。
 *
 * 与 Material3 默认 TopAppBar 的差异：
 *   - 标题使用 [LocalZhixingSpacing] token 控制的紧凑内边距 + titleLarge 字号。
 *   - 提供可选返回按钮（[onBack]）与右侧操作区（[actions]）。
 *   - tonalElevation 取 token [LocalZhixingElevation] low，使顶栏与内容有柔和分层
 *     （不再依赖 Material 默认的 surface 同色无边感）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZhixingTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = LocalZhixingSpacing.current.xs),
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}
