package com.zhixing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhixing.data.DropScheduleCalculator
import com.zhixing.ui.theme.LocalZhixingElevation
import com.zhixing.ui.theme.LocalZhixingRadii
import com.zhixing.ui.theme.LocalZhixingSpacing
import com.zhixing.ui.theme.LocalZhixingStatus
import kotlin.math.roundToInt

/**
 * 周视图页面（时间格栅）。
 *
 * 冻结行（日期头）/ 冻结列（时间轴）布局，通过共享滚动状态实现同步：
 *   - 日期头 ↔ 7 天列 共享横向滚动状态（左右同步）；
 *   - 时间轴 ↔ 7 天列 共享纵向滚动状态（上下同步）。
 * 每列为 30 分钟格栅，项目按时间绝对定位，可通过滚动到达任意时段/日期。
 *
 * 拖放排期：backlog 区子项目通过长按拖拽，放入任意天列的任意行，
 * 落点由手指 root 坐标 + 双反滚动偏移反算。
 */
@Composable
fun WeekSchedulePage(
    weekDates: List<String>,
    itemsByDate: Map<String, List<ScheduleItem>>,
    backlogItems: List<BacklogItem> = emptyList(),
    onScheduleSubproject: (subprojectId: Long, date: String, startTime: Int, endTime: Int) -> Unit = { _, _, _, _ -> },
    onCompleteSubproject: (subprojectId: Long) -> Unit = {},
    onAbandonSubproject: (subprojectId: Long) -> Unit = {},
    onUnscheduleSubproject: (subprojectId: Long) -> Unit = {},
    // inline 面板编辑回写（名称 / 预期时间），由首页接 VM 持久化到数据库。
    onUpdateSubproject: (subprojectId: Long, title: String, estimatedDuration: Int?) -> Unit = { _, _, _ -> },
    // 折叠状态由首页 MainScreen 层级持有（与 isScheduleWeekView 同级），
    // 跨 tab / 日周切换时不会随 composition 销毁而丢失。页面本身只读 + 上报翻转请求。
    collapsed: Boolean = false,
    onCollapsedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val grid = ScheduleTimeGrid
    val rowHeight = 48.dp
    val labelWidth = 40.dp
    val dayColumnWidth = 120.dp
    val totalHeight = grid.totalHeight(rowHeight.value).dp
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val columnWidthPx = with(density) { dayColumnWidth.toPx() }
    // 真实像素，与 yPx 同单位，用于 clamp。
    val gridHeightPx = with(density) { totalHeight.toPx() }

    // 共享滚动状态
    val verticalScrollState = rememberScrollState()   // 时间轴 ↔ 天列（上下同步）
    val horizontalScrollState = rememberScrollState() // 日期头 ↔ 天列（左右同步）

    // 网格视口（天列的直接外层容器）在 root 中的几何，作为落点反算基准。
    // 视口自身不随横向/纵向滚动移动，其 left/top 是稳定的布局常量；
    // 若从单个天列捕获，滚出视口的列会再次上报 left/top=0 把好值覆盖掉。
    var gridTopPx by remember { mutableStateOf(0f) }
    var gridContentLeftPx by remember { mutableStateOf(0f) }

    // 拖放状态。
    var dragState by remember { mutableStateOf<DragState?>(null) }
    val backlogTops = remember { mutableStateMapOf<Long, Float>() }
    val backlogLefts = remember { mutableStateMapOf<Long, Float>() }
    // Backlog 区域顶部在 root 中的 y 像素。释放到 backlog 区域内（fingerRootY >= backlogTopPx）
    // 视为取消排期，避免依赖格栅坐标反算（gridTopPx 滚动后会过期，反算不可靠）。
    var backlogTopPx by remember { mutableStateOf(0f) }
    // 页面根容器（overlay 层）在 root 中的坐标，供 glimpse 把 root 像素转回容器本地。
    var containerTopPx by remember { mutableStateOf(0f) }
    var containerLeftPx by remember { mutableStateOf(0f) }

    // 已排期项目块的操作菜单（完成 / 放弃 / 回退）。
    var showBlockMenu by remember { mutableStateOf(false) }
    var menuTargetId by remember { mutableStateOf(0L) }
    val onBlockClick: (Long) -> Unit = { subprojectId ->
        menuTargetId = subprojectId
        showBlockMenu = true
    }

    // backlog 子项目 inline 面板：当前展开的药丸 id（null = 无）。点击药丸 toggle 展开/收起。
    var expandedPillId by remember { mutableStateOf<Long?>(null) }
    // 排期对话框目标 + 可见性（「排期」按钮 → 打开日期时间选择器，非立即排期）。
    var backlogScheduleTargetId by remember { mutableStateOf(0L) }
    var showBacklogScheduleDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(start = LocalZhixingSpacing.current.lg, end = LocalZhixingSpacing.current.lg, top = LocalZhixingSpacing.current.lg)
            .onGloballyPositioned {
                containerTopPx = it.boundsInRoot().top
                containerLeftPx = it.boundsInRoot().left
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部日期头行（与天列左右同步）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .horizontalScroll(horizontalScrollState),
            ) {
                weekDates.forEach { date ->
                    Column(
                        modifier = Modifier.width(dayColumnWidth).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = WeekDateUtils.dayLabel(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = date.substring(startIndex = 8),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            // 主体：时间轴 + 7 天列
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // 左侧时间轴（与天列上下同步）
                Column(modifier = Modifier.width(labelWidth).verticalScroll(verticalScrollState)) {
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

                // 7 天列（上下同步时间轴，左右同步日期头）
                // 此 Column 是网格视口容器：自身不随横向/纵向滚动移动，
                // 故其 boundsInRoot 是稳定的布局常量，作为落点反算的基准。
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(verticalScrollState)
                        .onGloballyPositioned {
                            if (gridTopPx == 0f) gridTopPx = it.boundsInRoot().top
                            if (gridContentLeftPx == 0f) gridContentLeftPx = it.boundsInRoot().left
                        },
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(horizontalScrollState),
                    ) {
                        weekDates.forEach { date ->
                            WeekDayGridColumn(
                                date = date,
                                items = itemsByDate[date] ?: emptyList(),
                                grid = grid,
                                rowHeight = rowHeight,
                                dayColumnWidth = dayColumnWidth,
                                totalHeight = totalHeight,
                                onBlockClick = onBlockClick,
                            )
                        }
                    }
                }
            }

            // 下部：Backlog 区（药丸流，不参与网格滚动，始终可见，作为拖放的 drag source）
            if (backlogItems.isNotEmpty()) {
                LongPressDragArea {
                  BacklogPillSection(
                    backlogItems = backlogItems,
                    dragState = dragState,
                    onPillClick = { id ->
                        // toggle 展开/收起 inline 面板。
                        expandedPillId = if (expandedPillId == id) null else id
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
                            handleWeekGridDrop(
                                subprojectId = state.subprojectId,
                                fingerRootX = state.fingerRootX,
                                fingerRootY = state.fingerRootY,
                                gridTopPx = gridTopPx,
                                gridContentLeftPx = gridContentLeftPx,
                                columnWidthPx = columnWidthPx,
                                horizontalScrollOffset = horizontalScrollState.value.toFloat(),
                                verticalScrollOffset = verticalScrollState.value.toFloat(),
                                gridHeightPx = gridHeightPx,
                                grid = grid,
                                rowHeightPx = rowHeightPx,
                                backlogItems = backlogItems,
                                weekDates = weekDates,
                                onScheduleSubproject = onScheduleSubproject,
                            )
                        }
                    },
                    onDragCancel = { dragState = null },
                    collapsed = collapsed,
                    onCollapsedChange = onCollapsedChange,
                  )
                }
            }
        }

        // backlog 子项目编辑面板：屏幕中央弹窗。
        // 全屏半透明遮罩 + 居中面板；点击遮罩（空白区域）→ 收起。
        val expandedItem = backlogItems.firstOrNull { it.id == expandedPillId }
        if (expandedItem != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { expandedPillId = null }
                    .testTag("BacklogPanelScrim"),
                contentAlignment = Alignment.Center,
            ) {
                InlineBacklogPanel(
                    item = expandedItem,
                    onScheduleClick = { id ->
                        // 「排期」→ 收起面板并打开日期时间选择器（非立即排期）。
                        backlogScheduleTargetId = id
                        expandedPillId = null
                        showBacklogScheduleDialog = true
                    },
                    onUpdateSubproject = onUpdateSubproject,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .padding(horizontal = LocalZhixingSpacing.current.lg)
                        // 点击面板主体不穿透到遮罩（避免空白处误收起）。
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { },
                )
            }
        }

        // 拖拽视觉提示（drag overlay）
        val state = dragState
        if (state != null) {
            val item = backlogItems.firstOrNull { it.id == state.subprojectId }
            if (item != null && containerTopPx != 0f) {
                DragGlimpse(
                    title = item.title,
                    offsetY = state.fingerRootY - containerTopPx,
                    offsetX = state.fingerRootX - containerLeftPx,
                )
            }
        }

        // 已排期项目块的操作菜单（完成 / 放弃 / 回退）。
        if (showBlockMenu) {
            AlertDialog(
                onDismissRequest = { showBlockMenu = false },
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                title = { Text("操作") },
                text = { Text("对这个子项目做什么？") },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                onUnscheduleSubproject(menuTargetId)
                                showBlockMenu = false
                            },
                            modifier = Modifier.testTag("UnscheduleSubprojectConfirm"),
                        ) { Text("回退") }
                        TextButton(
                            onClick = {
                                onCompleteSubproject(menuTargetId)
                                showBlockMenu = false
                            },
                            modifier = Modifier.testTag("CompleteSubprojectConfirm"),
                        ) { Text("完成") }
                        TextButton(
                            onClick = {
                                onAbandonSubproject(menuTargetId)
                                showBlockMenu = false
                            },
                        ) { Text("放弃") }
                    }
                },
            )
        }

        // backlog 子项目「排期」：由 inline 面板的按钮触发（backlogScheduleTargetId 已由面板设置）。
        // 完成/放弃在日程块菜单中已有（排期后的子项目进入格栅成为排期块），故不重复。
        if (showBacklogScheduleDialog) {
            ScheduleDateTimePickerDialog(
                initialDate = weekDates.first(),
                today = weekDates.first(),
                onConfirm = { selectedDate, start, end ->
                    onScheduleSubproject(backlogScheduleTargetId, selectedDate, start, end)
                    showBacklogScheduleDialog = false
                },
                onDismiss = { showBacklogScheduleDialog = false },
            )
        }
    }
}

@Composable
private fun WeekDayGridColumn(
    date: String,
    items: List<ScheduleItem>,
    grid: TimeGridLayout,
    rowHeight: androidx.compose.ui.unit.Dp,
    dayColumnWidth: androidx.compose.ui.unit.Dp,
    totalHeight: androidx.compose.ui.unit.Dp,
    onBlockClick: (Long) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .width(dayColumnWidth)
            .height(totalHeight)
            .testTag("WeekDay-$date"),
    ) {
        // 横向分隔线（形成格栅）
        Column(modifier = Modifier.fillMaxSize()) {
            repeat(grid.rowCount) { row ->
                Box(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        // 项目块（按时间绝对定位）
        items.sortedBy { it.startTime }.forEach { item ->
            val dimmed = item.subprojectStatus in TERMINAL_SUBPROJECT || item.isOverdue
            val yPx = grid.yPositionFor(item.startTime, rowHeight.value)
            val hPx = grid.heightFor(item.startTime, item.endTime, rowHeight.value)
            WeekScheduleBlock(
                item = item,
                dimmed = dimmed,
                yPx = yPx,
                hPx = hPx,
                onClick = { onBlockClick(item.subprojectId) },
            )
        }
        }
    }

// 周视图已排项 — 单独 composable 隔离"放置弹入"动画状态，
// 避免将 placed 状态提到 forEach 外层。
@Composable
private fun WeekScheduleBlock(
    item: ScheduleItem,
    dimmed: Boolean,
    yPx: Float,
    hPx: Float,
    onClick: (() -> Unit)? = null,
) {
    val textColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // 已排项放置弹入：scale 0.92 → 1.0 轻弹，模拟"放入时间格"的手感。
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { placed = true }
    val placeScale by animateFloatAsState(
        targetValue = if (placed) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "weekBlockPlace",
    )

    Box(
        modifier = Modifier
            .offset(y = yPx.dp)
            .height(hPx.dp)
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = 1.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .testTag("WeekItem-${item.id}")
            .semantics { this.dimmed = dimmed },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .scale(placeScale)
                .then(if (dimmed) Modifier.alpha(0.5f) else Modifier),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = LocalZhixingElevation.current.low,
            shape = RoundedCornerShape(LocalZhixingRadii.current.sm),
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态图标：已完成（绿钩）优先于逾期（灰闹钟）。放弃的块已被删除，不会进入此函数。
                // 尺寸 12dp，适配周视图更密的行高；与"已终结"纯弱化（无图标）区分。
                val weekIcon = when {
                    item.subprojectStatus == "已完成" ->
                        Triple(Icons.Filled.Check, LocalZhixingStatus.current.doneFg, "已完成")
                    item.isOverdue ->
                        Triple(Icons.Filled.Alarm, MaterialTheme.colorScheme.onSurfaceVariant, "逾期")
                    else -> null
                }
                if (weekIcon != null) {
                    Icon(
                        imageVector = weekIcon.first,
                        contentDescription = weekIcon.third,
                        tint = weekIcon.second,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                // 标题占满剩余空间（weight），时间段始终完整显示在右侧同行。
                Text(
                    text = item.subprojectTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTimeRange(item.startTime, item.endTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 周视图格栅落点处理：由手指 root 坐标 + 双向滚动偏移反算日期与时间段。
 *
 * 横向：contentX = fingerRootX + horizontalScrollOffset，命中 [colLeft, colLeft+colWidth) → 日期；
 * 纵向：yPx = fingerRootY - gridTopPx + verticalScrollOffset → 时间段。
 */
private fun handleWeekGridDrop(
    subprojectId: Long,
    fingerRootX: Float,
    fingerRootY: Float,
    gridTopPx: Float,
    gridContentLeftPx: Float,
    columnWidthPx: Float,
    horizontalScrollOffset: Float,
    verticalScrollOffset: Float,
    gridHeightPx: Float,
    grid: TimeGridLayout,
    rowHeightPx: Float,
    backlogItems: List<BacklogItem>,
    weekDates: List<String>,
    onScheduleSubproject: (Long, String, Int, Int) -> Unit,
) {
    if (gridTopPx <= 0f || gridContentLeftPx <= 0f) return
    // 横向：以首列 left 为基准，按列宽推算列索引 → 日期。
    // 不依赖各列独立 left（horizontalScroll 容器内屏幕外列的 boundsInRoot 不可靠），
    // 而用列索引法：所有列等宽，索引 = (contentX - firstColLeft) / colWidth。
    val contentX = fingerRootX + horizontalScrollOffset
    val colIndex = ((contentX - gridContentLeftPx) / columnWidthPx).toInt()
    if (colIndex !in weekDates.indices) return
    val date = weekDates[colIndex]
    // 纵向：判定落在哪一行 → 时间段。
    val yPx = fingerRootY - gridTopPx + verticalScrollOffset
    if (yPx < 0f || yPx > gridHeightPx) return
    val backlogItem = backlogItems.firstOrNull { it.id == subprojectId } ?: return
    val duration = backlogItem.estimatedDuration?.takeIf { it > 0 } ?: 30
    val slot = DropScheduleCalculator.slotFromDrop(yPx, rowHeightPx, grid, duration)
    onScheduleSubproject(subprojectId, date, slot.start, slot.end)
}

/**
 * backlog 子项目的编辑面板（屏幕中央弹窗，由页面根 overlay 渲染）。
 *
 * 内容含：
 *   - 名称输入框（预填原标题，本地状态；编辑经 [onUpdateSubproject] 持久化）
 *   - 预期时间输入框（预填原时长，数字键盘）
 *   - 「排期」按钮 → onScheduleClick(id)（由页面打开日期时间选择器）
 *
 * [modifier] 由页面根 overlay 注入（宽度约束 + 点击拦截），以居中卡片形式呈现。
 *
 * 编辑持久化：本地状态保证输入响应；[name]/[duration] 变化落定后，
 * 经 LaunchedEffect 回写数据库（初回组合写入原值是 no-op，无副作用）。
 *
 * 放弃的块已被删除，不会进入此函数（面板仅对 backlog 药丸渲染）。
 */
@Composable
private fun InlineBacklogPanel(
    item: BacklogItem,
    onScheduleClick: (Long) -> Unit,
    onUpdateSubproject: (id: Long, title: String, estimatedDuration: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(item.title) }
    var duration by remember { mutableStateOf(item.estimatedDuration?.toString() ?: "") }

    // 名称 / 预期时间落定后持久化回写（本地状态保持输入响应）。
    LaunchedEffect(name, duration) {
        val minutes = duration.toIntOrNull()
        onUpdateSubproject(item.id, name, minutes)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LocalZhixingSpacing.current.sm)
            .testTag("BacklogPanel-${item.id}"),
        shape = RoundedCornerShape(LocalZhixingRadii.current.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = LocalZhixingElevation.current.low,
    ) {
        Column(
            modifier = Modifier.padding(LocalZhixingSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalZhixingSpacing.current.sm),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("BacklogPanelName-${item.id}"),
            )
            OutlinedTextField(
                value = duration,
                onValueChange = { value -> duration = value.filter { it.isDigit() } },
                label = { Text("预期时间（分钟）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("BacklogPanelDuration-${item.id}"),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onScheduleClick(item.id) },
                    modifier = Modifier.testTag("BacklogPanelSchedule-${item.id}"),
                ) { Text("排期") }
            }
        }
    }
}

/**
 * Backlog 药丸流面板（与日视图共用同一 UI 单元，迁移到周视图）。
 *
 * 顶部"待排期 N"徽章 + FlowRow 药丸列表。每个药丸同时支持：
 *   - tap → onPillClick(id)
 *   - long-press-drag → 通过 onDragStart/onDrag/onDragEnd 把子项目排到天格栅
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
    // 折叠状态由页面外部（最终为首页 MainScreen 层级）持有，页面只读 + 上报翻转请求。
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("BacklogSection")
            .onGloballyPositioned { onBacklogPositioned(it.boundsInRoot().top) },
    ) {
        val backlineIconRotation by animateFloatAsState(
            targetValue = if (collapsed) 0f else 180f,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "backlogArrow",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCollapsedChange(!collapsed) }
                .testTag("BacklogHeader")
                .heightIn(min = 48.dp)
                .padding(top = LocalZhixingSpacing.current.lg, bottom = LocalZhixingSpacing.current.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("待排期", style = MaterialTheme.typography.titleMedium)
            // 箭头随展开/收起旋转 180°，折叠态箭头朝右、展开态朝下，
            // spring 旋转替代显隐，让状态变化更连贯、有弹性。
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (collapsed) "展开" else "收起",
                modifier = Modifier.rotate(backlineIconRotation),
            )
        }
        // 展开/收起动画：fade + 竖向展开，采用 tween 缓动替代弹簧——
        // 无过冲振荡，避免展开时"抖动大"的廉价感（弹簧阻尼低会反复弹）。
        AnimatedVisibility(
            visible = !collapsed,
            enter = fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) + expandVertically(
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            ),
            exit = fadeOut(animationSpec = tween(durationMillis = 250)) + shrinkVertically(
                animationSpec = tween(durationMillis = 250),
            ),
        ) {
            // 按父任务归组；每组可独立折叠/展开（页面内临时态，页面销毁即重置）。
            val groups = BacklogGrouper.group(backlogItems)
            val collapsedGroups = remember { mutableStateMapOf<Long, Boolean>() }
            Column(modifier = Modifier.fillMaxWidth()) {
                groups.forEach { group ->
                    val groupCollapsed = collapsedGroups[group.taskId] ?: false
                    val groupIconRotation by animateFloatAsState(
                        targetValue = if (groupCollapsed) 0f else 180f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        label = "groupArrow",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { collapsedGroups[group.taskId] = !groupCollapsed }
                            .testTag("TaskGroupHeader-${group.taskId}")
                            .heightIn(min = 48.dp)
                            .padding(top = LocalZhixingSpacing.current.sm, bottom = LocalZhixingSpacing.current.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(group.taskTitle, style = MaterialTheme.typography.titleSmall)
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = if (groupCollapsed) "展开" else "收起",
                            modifier = Modifier.rotate(groupIconRotation),
                        )
                    }
                    // 任务组折叠/展开：同样用 tween 缓动，与顶层 Backlog 折叠手感一致、无抖动。
                    AnimatedVisibility(
                        visible = !groupCollapsed,
                        enter = fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)) + expandVertically(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 250)) + shrinkVertically(
                            animationSpec = tween(durationMillis = 250),
                        ),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalZhixingSpacing.current.sm)) {
                            group.items.forEach { backlogItem ->
                                val isDragging = dragState?.subprojectId == backlogItem.id
                                // 拖拽启动时药丸 scale 微放大 + 40% 透明，
                                // 轻弹反馈"被拿起"的重量感（LowBouncy 轻弹不抖）。
                                val pillScale by animateFloatAsState(
                                    targetValue = if (isDragging) 1.05f else 1f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessLow,
                                    ),
                                    label = "pillScale",
                                )
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
                                        .scale(pillScale)
                                        .then(if (isDragging) Modifier.alpha(0.4f) else Modifier),
                                    shape = RoundedCornerShape(LocalZhixingRadii.current.pill),
                                    color = LocalZhixingStatus.current.backlogBg,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = LocalZhixingSpacing.current.md, vertical = LocalZhixingSpacing.current.sm),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(LocalZhixingSpacing.current.sm),
                                    ) {
                                        Text(
                                            text = backlogItem.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = LocalZhixingStatus.current.backlogFg,
                                            maxLines = 1,
                                        )
                                        backlogItem.estimatedDuration?.let { dur ->
                                            Text(
                                                text = "${dur}分",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LocalZhixingStatus.current.backlogFg,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}