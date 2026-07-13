package com.zhixing.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.scale
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
import com.zhixing.ui.theme.LocalZhixingElevation
import com.zhixing.ui.theme.LocalZhixingRadii
import com.zhixing.ui.theme.LocalZhixingSpacing
import com.zhixing.ui.theme.LocalZhixingStatus
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

    // backlog 子项目 inline 面板：当前展开的药丸 id（null = 无）。点击药丸 toggle 展开/收起。
    var expandedPillId by remember { mutableStateOf<Long?>(null) }
    // 排期对话框目标 + 可见性（「排期」按钮 → 打开日期时间选择器，非立即排期）。
    var backlogScheduleTargetId by remember { mutableStateOf(0L) }
    var showBacklogScheduleDialog by remember { mutableStateOf(false) }
    // 面板内最新编辑的预期时间（分钟），按子项目 id 索引。
    // 面板每次编辑经 onDurationChange 同步到这里；不随 overlay 销毁，
    // 供排期对话框与拖拽路径即时使用（不依赖 VM 异步刷新时序）。
    val latestDurationById = remember { mutableStateMapOf<Long, Int>() }

    // 用 Box 包裹内容 + glimpse overlay。overlay 在同一个坐标容器内，
    // 从而用 fingerRootY - containerTopPx 就能把 glimpse 定位到手指位置。
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
                Spacer(Modifier.height(LocalZhixingSpacing.current.lg))
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
                            handleGridDrop(
                                subprojectId = state.subprojectId,
                                fingerRootY = state.fingerRootY,
                                gridTopPx = gridTopPx,
                                scrollOffsetPx = scrollState.value.toFloat(),
                                gridHeightPx = gridHeightPx,
                                grid = grid,
                                rowHeightPx = rowHeightPx,
                                backlogItems = backlogItems,
                                latestDurationById = latestDurationById,
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
                // M3 AlertDialog 默认 surface + tonalElevation=6dp 强叠加 surfaceTint，
                // 在浅色 surface 上会被感知为淡紫蓝；钉死暖白容器并归零 tonal 脱离叠加。
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
                initialDate = date,
                today = date,
                suggestedDurationMinutes = latestDurationById[backlogScheduleTargetId]
                    ?: backlogItems.firstOrNull { it.id == backlogScheduleTargetId }?.estimatedDuration
                    ?: 60,
                onConfirm = { selectedDate, start, end ->
                    onScheduleSubproject(backlogScheduleTargetId, start, end)
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

    // 已排项放置弹入：首次组合时 scale 从 0.92 → 1.0 轻弹，
    // 模拟"放入时间格"的活泼手感（原有项目不重复触发）。
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { placed = true }
    val placeScale by animateFloatAsState(
        targetValue = if (placed) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "blockPlace",
    )

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
                .scale(placeScale)
                .then(if (dimmed) Modifier.alpha(0.5f) else Modifier),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = LocalZhixingElevation.current.low,
            shape = RoundedCornerShape(LocalZhixingRadii.current.sm),
        ) {
            Row(
                modifier = Modifier.padding(LocalZhixingSpacing.current.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态图标：已完成（绿钩）优先于逾期（灰闹钟）。放弃的块已被删除，不会进入此函数。
                // 尺寸 16dp，语义一眼可辨；与"已终结"纯弱化（无图标）区分。
                val dayIcon = when {
                    item.subprojectStatus == "已完成" ->
                        Triple(Icons.Filled.Check, LocalZhixingStatus.current.doneFg, "已完成")
                    item.isOverdue ->
                        Triple(Icons.Filled.Alarm, MaterialTheme.colorScheme.onSurfaceVariant, "逾期")
                    else -> null
                }
                if (dayIcon != null) {
                    Icon(
                        imageVector = dayIcon.first,
                        contentDescription = dayIcon.third,
                        tint = dayIcon.second,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                // 标题占满剩余空间（weight），放不下的省略号截断；时间段始终完整显示在右侧同行。
                Text(
                    text = item.subprojectTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
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
    latestDurationById: Map<Long, Int>,
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
    // 优先使用面板里刚编辑过的预期时间（latestDurationById），拖拽路径即时同步；
    // 回落到 estimatedDuration 兜底。
    val duration = latestDurationById[subprojectId]
        ?: backlogItem.estimatedDuration?.takeIf { it > 0 } ?: 30
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
                .padding(bottom = LocalZhixingSpacing.current.sm),
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
    // 把面板内最新编辑的预期时间同步到 overlay，供排期对话框即时使用（不依赖 VM 异步刷新时序）。
    onDurationChange: (Int?) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(item.title) }
    var duration by remember { mutableStateOf(item.estimatedDuration?.toString() ?: "") }

    // 名称 / 预期时间落定后持久化回写（本地状态保持输入响应）。
    // 同步最新值到 overlay，让排期对话框即时拿到面板里编辑的预期时间。
    LaunchedEffect(name, duration) {
        val minutes = duration.toIntOrNull()
        onUpdateSubproject(item.id, name, minutes)
        onDurationChange(minutes)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = LocalZhixingSpacing.current.sm)
            .testTag("BacklogPanel-${item.id}"),
        shape = RoundedCornerShape(LocalZhixingRadii.current.md),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = LocalZhixingElevation.current.low,
        // 柔和阴影让面板从透明背景中浮起，与背景区分。
        shadowElevation = LocalZhixingElevation.current.medium,
    ) {
        Column(
            modifier = Modifier.padding(LocalZhixingSpacing.current.md),
            verticalArrangement = Arrangement.spacedBy(LocalZhixingSpacing.current.md),
        ) {
            // 标题行：图标 + 「编辑子项目」，传达面板用途。
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LocalZhixingSpacing.current.sm),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "编辑子项目",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
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
            // 主次按钮分层：「取消」（文本）+「排期」（实心主按钮）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onClose() },
                    modifier = Modifier.testTag("BacklogPanelCancel-${item.id}"),
                ) { Text("取消") }
                Spacer(modifier = Modifier.width(LocalZhixingSpacing.current.sm))
                Button(
                    onClick = { onScheduleClick(item.id) },
                    modifier = Modifier.testTag("BacklogPanelSchedule-${item.id}"),
                ) { Text("排期") }
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
    // 拖拽幻影出现时从 0.8 → 1.0 弹出，轻弹反馈"幻影从指尖浮现"。
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val glimpseScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.8f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glimpseAppear",
    )
    Surface(
        modifier = Modifier
            // 绝对定位：容器左上角为原点，移到手指位置
            .offset {
                IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
            }
            // 让卡片宽度自适应内容，不撑满容器
            .wrapContentSize(unbounded = false)
            .scale(glimpseScale),
        shape = RoundedCornerShape(LocalZhixingRadii.current.sm),
        // 拖拽幻影容器钉死 primaryContainer（暖薄荷浅底）；
        // tonalElevation 归零——原 high=6dp 叠加 surfaceTint 最强，易泛冷蓝紫。
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = LocalZhixingElevation.current.medium,
    ) {
        // testTag 放在 Text 节点上，使其同时携带文本与语义标签，
        // 便于断言时直接命中"带标题的拖拽提示"。
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .padding(horizontal = LocalZhixingSpacing.current.md, vertical = LocalZhixingSpacing.current.sm)
                .testTag("DragGlimpse"),
            maxLines = 1,
        )
    }
}
