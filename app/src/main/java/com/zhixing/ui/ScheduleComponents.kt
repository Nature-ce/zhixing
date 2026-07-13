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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.zhixing.ui.theme.LocalZhixingElevation
import com.zhixing.ui.theme.LocalZhixingRadii
import com.zhixing.ui.theme.LocalZhixingSpacing
import com.zhixing.ui.theme.LocalZhixingStatus

/**
 * backlog 子项目的编辑面板（屏幕中央弹窗，由页面根 overlay 渲染）。
 *
 * 内容含：
 *   - 标题行（图标 + "编辑子项目"）
 *   - 名称输入框（预填原标题，本地状态；编辑经 [onUpdateSubproject] 持久化）
 *   - 预期时间输入框（预填原时长，数字键盘）
 *   - 「取消」（文本）+「排期」（实心主按钮）主次分层
 *
 * [modifier] 由页面根 overlay 注入（宽度约束 + 点击拦截），以居中卡片形式呈现。
 *
 * 编辑持久化：本地状态保证输入响应；[name]/[duration] 变化落定后，
 * 经 LaunchedEffect 回写数据库 + 经 [onDurationChange] 同步最新值到 overlay，
 * 让排期对话框与拖拽路径即时拿到面板里编辑的预期时间（不依赖 VM 异步刷新时序）。
 *
 * 放弃的块已被删除，不会进入此函数（面板仅对 backlog 药丸渲染）。
 */
@Composable
internal fun InlineBacklogPanel(
    item: BacklogItem,
    onScheduleClick: (Long) -> Unit,
    onUpdateSubproject: (id: Long, title: String, estimatedDuration: Int?) -> Unit,
    onDurationChange: (Int?) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(item.title) }
    var duration by remember { mutableStateOf(item.estimatedDuration?.toString() ?: "") }

    // 名称 / 预期时间落定后持久化回写（本地状态保持输入响应）。
    // 同步最新值到 overlay，让排期对话框即时拿到面板里编辑的预期时间。
    val minutes = duration.toIntOrNull()
    LaunchedEffect(name, duration) {
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
 * 已排期项目块的操作菜单（回退 / 完成 / 放弃）。
 *
 * 三个操作分别回调 [onUnschedule] / [onComplete] / [onAbandon]，
 * 每个回调由调用方负责关闭菜单。点击遮罩则 [onDismiss]。
 *
 * 钉死暖白容器 + tonalElevation=0，脱离 M3 AlertDialog 默认 6dp surfaceTint 叠加，
 * 杜绝淡蓝紫味。
 */
@Composable
internal fun ScheduleBlockMenu(
    onUnschedule: () -> Unit,
    onComplete: () -> Unit,
    onAbandon: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text("操作") },
        text = { Text("对这个子项目做什么？") },
        confirmButton = {
            Row {
                TextButton(
                    onClick = onUnschedule,
                    modifier = Modifier.testTag("UnscheduleSubprojectConfirm"),
                ) { Text("回退") }
                TextButton(
                    onClick = onComplete,
                    modifier = Modifier.testTag("CompleteSubprojectConfirm"),
                ) { Text("完成") }
                TextButton(onClick = onAbandon) { Text("放弃") }
            }
        },
    )
}

/**
 * Backlog 药丸流面板（日 / 周视图共用）。
 *
 * 顶部"待排期"N 徽章 + FlowRow 药丸列表。每个药丸同时支持：
 *   - tap → onPillClick(id)（打开排期菜单）
 *   - long-press-drag → 通过 onDragStart/onDrag/onDragEnd 把子项目排到格栅
 *
 * 药丸用 Surface 自定义而非 InputChip，以便 clickable 与 detectDragGesturesAfterLongPress
 * 同链共存、由页面层统一处理两套手势。
 *
 * 仅 [headerTopPadding] 一项区分两种视图：日视图 0.dp / 周视图 lg，
 * 适配两者在时间轴上方留白差异。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BacklogPillSection(
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
    // 视图区分：日视图 0.dp / 周视图 LocalZhixingSpacing.lg。
    headerTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
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
                .padding(top = headerTopPadding, bottom = LocalZhixingSpacing.current.sm),
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
 * 日 / 周视图公用的已排项渲染块。
 *
 * 结构相同，仅尺寸与标签前缀随视图而异：
 *   - 日视图：图标 16dp、标题 bodySmall、水平 padding 2.dp、标签 `ScheduleBlock-{id}`
 *   - 周视图：图标 12dp、标题 labelSmall 10sp、水平 padding 1.dp、标签 `WeekItem-{id}`
 *
 * dimmed 在内部按 item 状态计算（纯函数），调用方无需传入。
 */
@Composable
internal fun ScheduleBlock(
    item: ScheduleItem,
    yPx: Float,
    hPx: Float,
    onClick: ((Long) -> Unit)? = null,
    iconSize: Dp,
    titleStyle: TextStyle,
    timeFontSize: TextUnit,
    horizontalPadding: Dp,
    rowPadding: Dp,
    tagPrefix: String,
    placeAnimationLabel: String,
    // 长按拖拽重排：定位回调 + 拖拽手势回调（仅日视图已排项块使用，默认 null = 不可拖）。
    onBlockPositioned: ((id: Long, top: Float, left: Float) -> Unit)? = null,
    onDragStart: ((Long) -> Unit)? = null,
    onDrag: ((change: PointerInputChange, id: Long) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    onDragCancel: (() -> Unit)? = null,
) {
    val dimmed = item.subprojectStatus in setOf("已完成", "已放弃") || item.isOverdue
    val textColor = if (dimmed) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    // 已排项放置弹入：首次组合时 scale 从 0.92 → 1.0 轻弹，模拟"放入时间格"的手感。
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { placed = true }
    val placeScale by animateFloatAsState(
        targetValue = if (placed) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = placeAnimationLabel,
    )

    Box(
        modifier = Modifier
            .offset(y = yPx.dp)
            .height(hPx.dp)
            .fillMaxWidth()
            .padding(vertical = 1.dp, horizontal = horizontalPadding)
            .then(if (onClick != null) Modifier.clickable { onClick(item.subprojectId) } else Modifier)
            .then(
                if (onBlockPositioned != null) {
                    Modifier.onGloballyPositioned {
                        onBlockPositioned(item.subprojectId, it.boundsInRoot().top, it.boundsInRoot().left)
                    }
                } else Modifier,
            )
            .then(
                if (onDragStart != null) {
                    Modifier.pointerInput(item.subprojectId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart(item.subprojectId) },
                            onDrag = { change, _ -> onDrag?.invoke(change, item.subprojectId) },
                            onDragEnd = { onDragEnd?.invoke() },
                            onDragCancel = { onDragCancel?.invoke() },
                        )
                    }
                } else Modifier,
            )
            .testTag("$tagPrefix-${item.id}")
            .semantics { this.dimmed = dimmed },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .scale(placeScale),
            // 背景始终不透明：块跨过的格线被背景遮盖而"消失"，
            // 弱化（dimmed）只作用于前景内容（图标+文字），不透明背景保证格线不透过。
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = LocalZhixingElevation.current.low,
            // 阴影让块从格栅中"浮起"，强化块与背景的层次，看起来更立体。
            shadowElevation = LocalZhixingElevation.current.medium,
            shape = RoundedCornerShape(LocalZhixingRadii.current.sm),
        ) {
            Row(
                modifier = Modifier
                    .padding(rowPadding)
                    .then(if (dimmed) Modifier.alpha(0.5f) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 状态图标：已完成（绿钩）优先于逾期（灰闹钟）。放弃的块已被删除，不会进入此函数。
                val iconTriple = when {
                    item.subprojectStatus == "已完成" ->
                        Triple(Icons.Filled.Check, LocalZhixingStatus.current.doneFg, "已完成")
                    item.isOverdue ->
                        Triple(Icons.Filled.Alarm, MaterialTheme.colorScheme.onSurfaceVariant, "逾期")
                    else -> null
                }
                if (iconTriple != null) {
                    Icon(
                        imageVector = iconTriple.first,
                        contentDescription = iconTriple.third,
                        tint = iconTriple.second,
                        modifier = Modifier.size(iconSize),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                // 标题占满剩余空间（weight），时间段始终完整显示在右侧同行。
                Text(
                    text = item.subprojectTitle,
                    style = titleStyle,
                    color = textColor,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTimeRange(item.startTime, item.endTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = timeFontSize,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 拖拽预览幽灵块：覆在格栅目标位置上，半透明显示落点与时长。
 *
 * 正常态用 [MaterialTheme.colorScheme.primary]，冲突态切 [MaterialTheme.colorScheme.error]（红）——
 * 形状不变仅变色，色盲可辨。绝对定位由调用方算好 [yPx]/[hPx]（像素），
 * 与 [ScheduleBlock] 同坐标系，从而精确对齐。
 */
@Composable
internal fun DragGhost(
    yPx: Float,
    hPx: Float,
    startTime: Int,
    endTime: Int,
    isConflict: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (isConflict) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .offset(y = yPx.dp)
            .height(hPx.dp)
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(LocalZhixingRadii.current.sm))
            .border(1.dp, color, RoundedCornerShape(LocalZhixingRadii.current.sm))
            .testTag("DragGhost")
            // 暴露冲突态供测试断言（颜色断言在 instrumented 里不稳定，语义属性更可靠）。
            .semantics { this.isConflict = isConflict },
    ) {
        Text(
            text = formatTimeRange(startTime, endTime),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/** 语义标记：拖拽预览幽灵块是否处于冲突态（落点与同任务已排项重叠）。 */
val ScheduleGhostConflictKey = SemanticsPropertyKey<Boolean>("ScheduleGhostConflict")
var SemanticsPropertyReceiver.isConflict by ScheduleGhostConflictKey
