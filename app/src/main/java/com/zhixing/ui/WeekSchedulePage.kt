package com.zhixing.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhixing.data.DropScheduleCalculator
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
    var containerTopPx by remember { mutableStateOf(0f) }
    var containerLeftPx by remember { mutableStateOf(0f) }

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
                            )
                        }
                    }
                }
            }

            // 下部：Backlog 区（不参与网格滚动，始终可见，作为拖放的 drag source）
            if (backlogItems.isNotEmpty()) {
                Text(
                    text = "Backlog",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Column(
                    modifier = Modifier.testTag("BacklogSection"),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
                ) {
                    backlogItems.forEach { backlogItem ->
                        val isDragging = dragState?.subprojectId == backlogItem.id
                        Text(
                            text = backlogItem.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .testTag("BacklogItem-${backlogItem.id}")
                                .onGloballyPositioned {
                                    backlogTops[backlogItem.id] = it.boundsInRoot().top
                                    backlogLefts[backlogItem.id] = it.boundsInRoot().left
                                }
                                .pointerInput(backlogItem.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragState = DragState(
                                                subprojectId = backlogItem.id,
                                                fingerRootY = 0f,
                                                fingerRootX = 0f,
                                            )
                                        },
                                        onDrag = { change, _ ->
                                            val itemTop = backlogTops[backlogItem.id] ?: return@detectDragGesturesAfterLongPress
                                            val itemLeft = backlogLefts[backlogItem.id] ?: return@detectDragGesturesAfterLongPress
                                            dragState = dragState?.copy(
                                                fingerRootY = itemTop + change.position.y,
                                                fingerRootX = itemLeft + change.position.x,
                                            )
                                        },
                                        onDragEnd = {
                                            val state = dragState
                                            dragState = null
                                            if (state != null) {
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
                                        onDragCancel = {
                                            dragState = null
                                        },
                                    )
                                }
                                .then(
                                    if (isDragging) Modifier.alpha(0.4f) else Modifier,
                                ),
                        )
                    }
                }
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
                    .padding(vertical = 1.dp, horizontal = 1.dp)
                    .testTag("WeekItem-${item.id}")
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
                    Column(modifier = Modifier.padding(2.dp)) {
                        Text(
                            text = item.subprojectTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor,
                            fontSize = 10.sp,
                            maxLines = 1,
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
    }
}

private fun formatHourMinute(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "%02d:%02d".format(h, m)
}

/**
 * 将起止分钟数格式化为 "HH:MM - HH:MM" 时间段，与日视图一致。
 */
private fun formatTimeRange(startMinutes: Int, endMinutes: Int): String {
    return "${formatHourMinute(startMinutes)} - ${formatHourMinute(endMinutes)}"
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
