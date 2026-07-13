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
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
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
    // 已排项块长按拖拽到新日期/时段：日期 + 起止时间由落点决定，时长保持不变。
    onRescheduleSubproject: (subprojectId: Long, date: String, startTime: Int, endTime: Int) -> Unit = { _, _, _, _ -> },
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
    // 每个已排项块顶部在 root 中的像素（用于块拖拽时把局部坐标转为 root 坐标），key = 子项目 id。
    val blockTops = remember { mutableStateMapOf<Long, Float>() }
    val blockLefts = remember { mutableStateMapOf<Long, Float>() }
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

    // 已排项块长按拖拽重排：记录块位置 + 拖拽手势回调（复用共享 dragState）。
    val onBlockPositioned: (Long, Float, Float) -> Unit = { id, top, left ->
        blockTops[id] = top
        blockLefts[id] = left
    }
    val onBlockDragStart: (Long) -> Unit = { id ->
        dragState = DragState(subprojectId = id, fingerRootY = 0f, fingerRootX = 0f)
    }
    val onBlockDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Long) -> Unit = { change, id ->
        val top = blockTops[id]
        val left = blockLefts[id]
        if (top != null && left != null) {
            dragState = dragState?.copy(
                fingerRootY = top + change.position.y,
                fingerRootX = left + change.position.x,
            )
        }
    }
    val onBlockDragEnd: () -> Unit = {
        val state = dragState
        dragState = null
        if (state != null) {
            handleWeekBlockGridDrop(
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
                itemsByDate = itemsByDate,
                weekDates = weekDates,
                onRescheduleSubproject = onRescheduleSubproject,
            )
        }
    }
    val onBlockDragCancel: () -> Unit = { dragState = null }

    // backlog 子项目 inline 面板：当前展开的药丸 id（null = 无）。点击药丸 toggle 展开/收起。
    var expandedPillId by remember { mutableStateOf<Long?>(null) }
    // 排期对话框目标 + 可见性（「排期」按钮 → 打开日期时间选择器，非立即排期）。
    var backlogScheduleTargetId by remember { mutableStateOf(0L) }
    var showBacklogScheduleDialog by remember { mutableStateOf(false) }
    // 面板内最新编辑的预期时间（分钟）：按子项目 id 持有，供排期对话框和拖拽路径即时使用。
    val latestDurationById = remember { mutableStateMapOf<Long, Int>() }

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
                                onBlockPositioned = onBlockPositioned,
                                onDragStart = onBlockDragStart,
                                onDrag = onBlockDrag,
                                onDragEnd = onBlockDragEnd,
                                onDragCancel = onBlockDragCancel,
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
                                latestDurationById = latestDurationById,
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
                    .background(Color.Transparent)
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
                    // 把面板内最新编辑的预期时间同步到顶层 Map，供两条排期路径即时使用。
                    onDurationChange = { minutes ->
                        if (minutes != null) latestDurationById[expandedItem.id] = minutes
                    },
                    onClose = { expandedPillId = null },
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
            // 优先匹配 backlog 药丸（子项目 id 即药丸 id）；未命中则匹配已排项块（按 subprojectId）。
            val backlogItem = backlogItems.firstOrNull { it.id == state.subprojectId }
            val blockItem = if (backlogItem == null) {
                itemsByDate.values.firstOrNull { list ->
                    list.any { it.subprojectId == state.subprojectId }
                }?.firstOrNull { it.subprojectId == state.subprojectId }
            } else null
            val title = backlogItem?.title
                ?: blockItem?.subprojectTitle
                ?: state.subprojectId.toString()
            if (containerTopPx != 0f) {
                DragGlimpse(
                    title = title,
                    offsetY = state.fingerRootY - containerTopPx,
                    offsetX = state.fingerRootX - containerLeftPx,
                )
            }
        }

        // 已排期项目块的操作菜单（回退 / 完成 / 放弃）。
        if (showBlockMenu) {
            ScheduleBlockMenu(
                onUnschedule = {
                    onUnscheduleSubproject(menuTargetId)
                    showBlockMenu = false
                },
                onComplete = {
                    onCompleteSubproject(menuTargetId)
                    showBlockMenu = false
                },
                onAbandon = {
                    onAbandonSubproject(menuTargetId)
                    showBlockMenu = false
                },
                onDismiss = { showBlockMenu = false },
            )
        }

        // backlog 子项目「排期」：由 inline 面板的按钮触发（backlogScheduleTargetId 已由面板设置）。
        // 完成/放弃在日程块菜单中已有（排期后的子项目进入格栅成为排期块），故不重复。
        if (showBacklogScheduleDialog) {
            ScheduleDateTimePickerDialog(
                initialDate = weekDates.first(),
                today = weekDates.first(),
                suggestedDurationMinutes = latestDurationById[backlogScheduleTargetId]
                    ?: backlogItems.firstOrNull { it.id == backlogScheduleTargetId }?.estimatedDuration
                    ?: 60,
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
    onBlockPositioned: ((id: Long, top: Float, left: Float) -> Unit)? = null,
    onDragStart: ((Long) -> Unit)? = null,
    onDrag: ((change: androidx.compose.ui.input.pointer.PointerInputChange, id: Long) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
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
            ScheduleBlock(
                item = item,
                yPx = grid.yPositionFor(item.startTime, rowHeight.value),
                hPx = grid.heightFor(item.startTime, item.endTime, rowHeight.value),
                onClick = { onBlockClick(item.subprojectId) },
                iconSize = 12.dp,
                titleStyle = MaterialTheme.typography.labelSmall,
                timeFontSize = 9.sp,
                horizontalPadding = 1.dp,
                rowPadding = 2.dp,
                tagPrefix = "WeekItem",
                placeAnimationLabel = "weekBlockPlace",
                onBlockPositioned = onBlockPositioned,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
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
    latestDurationById: Map<Long, Int>,
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
    // 优先使用面板里刚编辑过的预期时间（latestDurationById），拖拽路径即时同步；
    // 回落到 estimatedDuration 兜底。
    val duration = latestDurationById[subprojectId]
        ?: backlogItem.estimatedDuration?.takeIf { it > 0 } ?: 30
    val slot = DropScheduleCalculator.slotFromDrop(yPx, rowHeightPx, grid, duration)
    onScheduleSubproject(subprojectId, date, slot.start, slot.end)
}

/**
 * 周视图已排项块拖拽落点处理：把块拖到新日期/时段，时长保持不变（与原位无关）。
 *
 * 横向反算列索引 → 日期；纵向反算行 → 时间段；时长从被拽的 ScheduleItem 读取。
 */
private fun handleWeekBlockGridDrop(
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
    itemsByDate: Map<String, List<ScheduleItem>>,
    weekDates: List<String>,
    onRescheduleSubproject: (Long, String, Int, Int) -> Unit,
) {
    if (gridTopPx <= 0f || gridContentLeftPx <= 0f) return
    val contentX = fingerRootX + horizontalScrollOffset
    val colIndex = ((contentX - gridContentLeftPx) / columnWidthPx).toInt()
    if (colIndex !in weekDates.indices) return
    val date = weekDates[colIndex]
    val yPx = fingerRootY - gridTopPx + verticalScrollOffset
    if (yPx < 0f || yPx > gridHeightPx) return
    // 在任意天的列表中按 subprojectId 找被拽的块，读出原时长。
    val item = itemsByDate.values.firstOrNull { list ->
        list.any { it.subprojectId == subprojectId }
    }?.firstOrNull { it.subprojectId == subprojectId } ?: return
    val duration = (item.endTime - item.startTime).coerceAtLeast(30)
    val slot = DropScheduleCalculator.slotFromDrop(yPx, rowHeightPx, grid, duration)
    onRescheduleSubproject(subprojectId, date, slot.start, slot.end)
}

