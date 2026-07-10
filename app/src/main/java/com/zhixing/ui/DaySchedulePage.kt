package com.zhixing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhixing.data.DropScheduleCalculator
import kotlin.math.roundToInt

/** 语义标记：排期块是否处于终态弱化（变灰）显示。 */
val ScheduleBlockDimmedKey = SemanticsPropertyKey<Boolean>("ScheduleBlockDimmed")
var SemanticsPropertyReceiver.dimmed by ScheduleBlockDimmedKey

/** 子项目终态集合（已完成 / 已放弃）：进入终态的排项目块做弱化显示。 */
val TERMINAL_SUBPROJECT = setOf("已完成", "已放弃")

/**
 * 日视图页面（时间格栅）。
 *
 * 单一纵向滚动容器：左侧时间轴（06:00-22:30）与主体格栅并排，
 * 滚动时二者天然同步。每个项目块按其 startTime/endTime 绝对定位，
 * 作为滚动内容的子节点，可通过滚动到达任意时段。
 * 无排期时显示空状态引导。
 */
@Composable
fun DaySchedulePage(
    date: String,
    scheduleItems: List<ScheduleItem>,
    backlogItems: List<BacklogItem> = emptyList(),
    onScheduleSubproject: (subprojectId: Long, startTime: Int, endTime: Int) -> Unit = { _, _, _ -> },
    onCompleteSubproject: (subprojectId: Long) -> Unit = {},
    onAbandonSubproject: (subprojectId: Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val grid = ScheduleTimeGrid
    val rowHeight = 48.dp
    val labelWidth = 48.dp
    val totalHeight = grid.totalHeight(rowHeight.value).dp
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    // 格栅总高度（真实像素），与 yPx 同单位，用于 clamp 落点。
    val gridHeightPx = with(density) { totalHeight.toPx() }

    // 记录格栅主体顶部相对根坐标的 y 像素，供 drop 反算落点。
    // 注意：这是布局时捕获的值，滚动后需用 scrollOffsetPx 补正。
    var gridTopPx by remember { mutableStateOf(0f) }
    // 格栅的滚动状态：滚动平移发生在绘制阶段，不触发重新布局，
    // 故 gridTopPx 在滚动后会"过期"，必须配合 scrollOffsetPx 使用。
    val scrollState = rememberScrollState()

    // 拖放状态：当前被拖的子项目 id + 手指在 root 中的像素坐标。
    var dragState by remember { mutableStateOf<DragState?>(null) }
    // 每个 backlog 药丸顶部在 root 中的像素（用于把手势局部坐标转为 root 坐标）。
    val backlogTops = remember { mutableStateMapOf<Long, Float>() }
    val backlogLefts = remember { mutableStateMapOf<Long, Float>() }
    // Backlog 区域顶部在 root 中的 y 像素。释放到 backlog 区域内（fingerRootY >= backlogTopPx）
    // 视为取消排期，避免依赖格栅坐标反算（gridTopPx 滚动后会过期，反算不可靠）。
    var backlogTopPx by remember { mutableStateOf(0f) }
    // 页面根容器（overlay 层）在 root 中的坐标，供 glimpse 把 root 像素转回容器本地。
    var containerTopPx by remember { mutableStateOf(0f) }
    var containerLeftPx by remember { mutableStateOf(0f) }

    // 已排期项目块的操作菜单（完成/放弃）。
    var showBlockMenu by remember { mutableStateOf(false) }
    var menuTargetId by remember { mutableStateOf(0L) }

    // backlog 子项目的排期菜单（仅「排期」一项；完成/放弃在日程块菜单已有，不重复）。
    var showBacklogMenu by remember { mutableStateOf(false) }
    var backlogMenuTargetId by remember { mutableStateOf(0L) }
    var showBacklogScheduleDialog by remember { mutableStateOf(false) }

    // 用 Box 包裹内容 + glimpse overlay。overlay 在同一个坐标容器内，
    // 从而用 fingerRootY - containerTopPx 就能把 glimpse 定位到手指位置。
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .onGloballyPositioned {
                containerTopPx = it.boundsInRoot().top
                containerLeftPx = it.boundsInRoot().left
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        if (scheduleItems.isEmpty() && backlogItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "今天还没有排期，去任务页安排吧",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ScheduleEmptyState"),
                )
            }
        } else {
            // 上部：时间格栅区（独立滚动，时间轴 + 主体并排同步）。
            // 与 bottom 的 backlog 分离，避免 verticalScroll 与拖放手势竞争。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                Row {
                    // 左侧时间轴（高度由内容撑开 = rowCount * rowHeight = totalHeight）
                    Column(modifier = Modifier.width(labelWidth)) {
                        repeat(grid.rowCount) { row ->
                            Box(modifier = Modifier.height(rowHeight), contentAlignment = Alignment.TopEnd) {
                                Text(
                                    text = grid.labelForRow(row),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.padding(end = 4.dp),
                                )
                            }
                        }
                    }

                    // 主体：固定高度盒，内部格栅线条 + 绝对定位的项目块
                    // 作为 drop target：承载 testTag，落点由页面层 pointerInput 统一计算
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalHeight)
                            .onGloballyPositioned { gridTopPx = it.boundsInRoot().top }
                            .testTag("ScheduleDropTarget"),
                    ) {
                        // 横向分隔线（形成格栅）
                        Column(modifier = Modifier.fillMaxSize()) {
                            repeat(grid.rowCount) { row ->
                                Box(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }

                        // 项目块（按时间绝对定位，作为滚动内容子节点）
                        scheduleItems.sortedBy { it.startTime }.forEach { item ->
                            ScheduleBlock(
                                item = item,
                                grid = grid,
                                rowHeight = rowHeight,
                                onClick = { subprojectId ->
                                    menuTargetId = subprojectId
                                    showBlockMenu = true
                                },
                            )
                        }
                    }
                }
            }

            // 下部：Backlog 区（药丸流，支持点击→排期菜单 + 长拖拖放到格栅）
            if (backlogItems.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                LongPressDragArea {
                  BacklogPillSection(
                    backlogItems = backlogItems,
                    dragState = dragState,
                    onPillClick = { id ->
                        backlogMenuTargetId = id
                        showBacklogMenu = true
                    },
                    onPillPositioned = { id, top, left ->
                        backlogTops[id] = top
                        backlogLefts[id] = left
                    },
                    onBacklogPositioned = { top -> backlogTopPx = top },
                    onDragStart = { id ->
                        dragState = DragState(
                            subprojectId = id,
                            fingerRootY = 0f,
                            fingerRootX = 0f,
                        )
                    },
                    onDrag = { change, id ->
                        val top = backlogTops[id] ?: return@BacklogPillSection
                        val left = backlogLefts[id] ?: return@BacklogPillSection
                        dragState = dragState?.copy(
                            fingerRootY = top + change.position.y,
                            fingerRootX = left + change.position.x,
                        )
                    },
                    onDragEnd = {
                        val state = dragState
                        dragState = null
                        if (state != null) {
                            // 释放回 backlog 区域 → 取消排期（不依赖格栅坐标反算，
                            // 因 gridTopPx 滚动后会过期，反算结果不可靠）。
                            if (backlogTopPx > 0f && state.fingerRootY >= backlogTopPx) {
                                return@BacklogPillSection
                            }
                            handleGridDrop(
                                subprojectId = state.subprojectId,
                                fingerRootY = state.fingerRootY,
                                gridTopPx = gridTopPx,
                                scrollOffsetPx = scrollState.value.toFloat(),
                                gridHeightPx = gridHeightPx,
                                grid = grid,
                                rowHeightPx = rowHeightPx,
                                backlogItems = backlogItems,
                                onScheduleSubproject = onScheduleSubproject,
                            )
                        }
                    },
                    onDragCancel = { dragState = null },
                  )
                }
            }
        }
        }

        // 拖拽视觉提示（drag overlay）：跟手移动的卡片，仅在拖拽进行中显示。
        val dragStateForOverlay = dragState
        if (dragStateForOverlay != null) {
            val item = backlogItems.firstOrNull { it.id == dragStateForOverlay.subprojectId }
            if (item != null && containerTopPx != 0f) {
                DragGlimpse(
                    title = item.title,
                    // 把手指 root 像素转为本容器本地坐标
                    offsetY = dragStateForOverlay.fingerRootY - containerTopPx,
                    offsetX = dragStateForOverlay.fingerRootX - containerLeftPx,
                )
            }
        }

        // 已排期项目块的操作菜单（完成/放弃）。
        if (showBlockMenu) {
            AlertDialog(
                onDismissRequest = { showBlockMenu = false },
                title = { Text("操作") },
                text = { Text("对这个子项目做什么？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onCompleteSubproject(menuTargetId)
                            showBlockMenu = false
                        },
                        modifier = Modifier.testTag("CompleteSubprojectConfirm"),
                    ) { Text("完成") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            onAbandonSubproject(menuTargetId)
                            showBlockMenu = false
                        },
                    ) { Text("放弃") }
                },
            )
        }

        // backlog 子项目的操作菜单：仅「排期」。
        // 完成/放弃在日程块菜单中已有（排期后的子项目进入格栅成为排期块），故不重复。
        if (showBacklogMenu) {
            AlertDialog(
                onDismissRequest = { showBacklogMenu = false },
                title = { Text("操作") },
                text = { Text("对这个子项目做什么？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showBacklogMenu = false
                            showBacklogScheduleDialog = true
                        },
                        modifier = Modifier.testTag("BacklogScheduleConfirm"),
                    ) { Text("排期") }
                },
                dismissButton = {
                    TextButton(onClick = { showBacklogMenu = false }) { Text("取消") }
                },
            )
        }

        if (showBacklogScheduleDialog) {
            ScheduleDateTimePickerDialog(
                initialDate = date,
                today = date,
                onConfirm = { selectedDate, start, end ->
                    onScheduleSubproject(backlogMenuTargetId, start, end)
                    showBacklogScheduleDialog = false
                },
                onDismiss = { showBacklogScheduleDialog = false },
            )
        }
    }
}

@Composable
private fun ScheduleBlock(
    item: ScheduleItem,
    grid: TimeGridLayout,
    rowHeight: androidx.compose.ui.unit.Dp,
    onClick: ((Long) -> Unit)? = null,
) {
    val dimmed = item.subprojectStatus in TERMINAL_SUBPROJECT || item.isOverdue
    val textColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val yPx = grid.yPositionFor(item.startTime, rowHeight.value)
    val hPx = grid.heightFor(item.startTime, item.endTime, rowHeight.value)

    Box(
        modifier = Modifier
            .offset(y = yPx.dp)
            .height(hPx.dp)
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = 2.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick(item.subprojectId) }
                } else {
                    Modifier
                }
            )
            .testTag("ScheduleBlock-${item.id}")
            .semantics { this.dimmed = dimmed },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .then(if (dimmed) Modifier.alpha(0.5f) else Modifier),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shape = MaterialTheme.shapes.small,
        ) {
            Column(modifier = Modifier.padding(4.dp)) {
                Text(
                    text = item.subprojectTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    maxLines = 1,
                )
                Text(
                    text = formatTimeRange(item.startTime, item.endTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/**
 * 将从 0 点起的分钟数转为 "HH:MM" 格式时间段。
 */
private fun formatTimeRange(startMinutes: Int, endMinutes: Int): String {
    return "${formatTime(startMinutes)} - ${formatTime(endMinutes)}"
}

private fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return "%02d:%02d".format(hours, mins)
}

/** 拖放中的状态：被拖的子项目 id + 手指在 root 坐标中的像素坐标。 */
internal data class DragState(
    val subprojectId: Long,
    val fingerRootY: Float,
    val fingerRootX: Float,
)

/**
 * 处理格栅落点：由手指 root 坐标反算格栅内 y 像素，再算出时间段，回调 onScheduleSubproject。
 *
 * 手指在格栅区域外（上方 / 下方 / 格栅未布局）时不做任何事。
 */
private fun handleGridDrop(
    subprojectId: Long,
    fingerRootY: Float,
    gridTopPx: Float,
    scrollOffsetPx: Float,
    gridHeightPx: Float,
    grid: TimeGridLayout,
    rowHeightPx: Float,
    backlogItems: List<BacklogItem>,
    onScheduleSubproject: (Long, Int, Int) -> Unit,
) {
    if (gridTopPx <= 0f) return
    // gridTopPx 是布局时的格栅顶部 root y；滚动格栅后，内容向下滚 scrollOffsetPx，
    // 格栅视觉顶部在 gridTopPx - scrollOffsetPx。手指相对格栅内容的位置需补正：
    //   yPx = fingerRootY - (gridTopPx - scrollOffsetPx)
    //       = fingerRootY - gridTopPx + scrollOffsetPx
    val yPx = fingerRootY - gridTopPx + scrollOffsetPx
    if (yPx < 0f || yPx > gridHeightPx) return
    val backlogItem = backlogItems.firstOrNull { it.id == subprojectId } ?: return
    val duration = backlogItem.estimatedDuration?.takeIf { it > 0 } ?: 30
    val slot = DropScheduleCalculator.slotFromDrop(yPx, rowHeightPx, grid, duration)
    onScheduleSubproject(subprojectId, slot.start, slot.end)
}

/**
 * Backlog 药丸流面板（方案 2 选定）。
 *
 * 顶部"待排期 N"徽章 + FlowRow 药丸列表。每个药丸同时支持：
 *   - tap → onPillClick(id)（打开排期菜单）
 *   - long-press-drag → 通过 onDragStart/onDrag/onDragEnd 把子项目排到格栅
 *
 * 药丸用 Surface 自定义而非 InputChip，以便 clickable 与 detectDragGesturesAfterLongPress
 * 同链共存、由页面层统一处理两套手势。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BacklogPillSection(
    backlogItems: List<BacklogItem>,
    dragState: DragState?,
    onPillClick: (Long) -> Unit,
    onPillPositioned: (id: Long, top: Float, left: Float) -> Unit,
    onBacklogPositioned: (top: Float) -> Unit,
    onDragStart: (Long) -> Unit,
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, id: Long) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("BacklogSection")
            .onGloballyPositioned { onBacklogPositioned(it.boundsInRoot().top) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("待排期", style = MaterialTheme.typography.titleMedium)
            Badge { Text("${backlogItems.size}") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            backlogItems.forEach { backlogItem ->
                val isDragging = dragState?.subprojectId == backlogItem.id
                Surface(
                    modifier = Modifier
                        .testTag("BacklogItem-${backlogItem.id}")
                        .onGloballyPositioned {
                            onPillPositioned(
                                backlogItem.id,
                                it.boundsInRoot().top,
                                it.boundsInRoot().left,
                            )
                        }
                        .pointerInput(backlogItem.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart(backlogItem.id) },
                                onDrag = { change, _ -> onDrag(change, backlogItem.id) },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragCancel() },
                            )
                        }
                        .clickable { onPillClick(backlogItem.id) }
                        .then(if (isDragging) Modifier.alpha(0.4f) else Modifier),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = backlogItem.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        backlogItem.estimatedDuration?.let { dur ->
                            Text(
                                text = "${dur}分",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 把药丸的 long-press-drag 长按超时从系统默认（~500ms）压到 [timeoutMillis]，
 * 使拖拽激活更灵敏。通过覆盖 [LocalViewConfiguration] 的 `longPressTimeoutMillis` 实现；
 * 其余参数（touchSlop 等）沿用系统默认。
 */
@Composable
internal fun LongPressDragArea(
    timeoutMillis: Long = 200L,
    content: @Composable () -> Unit,
) {
    val base = LocalViewConfiguration.current
    val viewConfiguration = object : ViewConfiguration {
        override val longPressTimeoutMillis: Long get() = timeoutMillis
        override val doubleTapTimeoutMillis: Long get() = base.doubleTapTimeoutMillis
        override val doubleTapMinTimeMillis: Long get() = base.doubleTapMinTimeMillis
        override val touchSlop: Float get() = base.touchSlop
        override val minimumTouchTargetSize get() = base.minimumTouchTargetSize
    }
    CompositionLocalProvider(LocalViewConfiguration provides viewConfiguration) {
        content()
    }
}

/**
 * 拖拽视觉提示：一张悬浮卡片，跟手移动，显示被拖子项目的标题。
 *
 * 仅在拖拽进行中渲染（由调用方以 dragState != null 控制）。
 * offsetX/offsetY 是手指在容器本地坐标中的位置；卡片以手指为基准
 * 向左偏移一半宽度并向上偏移，使卡片悬于手指上方不遮挡落点。
 */
@Composable
internal fun DragGlimpse(
    title: String,
    offsetX: Float,
    offsetY: Float,
) {
    Surface(
        modifier = Modifier
            // 绝对定位：容器左上角为原点，移到手指位置
            .offset {
                IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
            }
            // 让卡片宽度自适应内容，不撑满容器
            .wrapContentSize(unbounded = false),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
    ) {
        // testTag 放在 Text 节点上，使其同时携带文本与语义标签，
        // 便于断言时直接命中"带标题的拖拽提示"。
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("DragGlimpse"),
            maxLines = 1,
        )
    }
}
