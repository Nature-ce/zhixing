package com.zhixing.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 排期日期 + 时间选择对话框。
 *
 * 顶部选日期（点击行弹出 DatePickerDialog），下方仅选**开始时间**；
 * 结束时间由「开始 + 建议时长 ([suggestedDurationMinutes])」自动计算并回显，
 * 用户无需手动输入结束时间。
 * 校验：
 *   - 结束必须晚于开始（[isValidSchedule]；建议时长 > 0 时恒成立）
 *   - 所选日期不能早于 today（[isValidScheduleDate]），
 *     否则禁用确认按钮并显示"不能排到过去"提示。
 *
 * 回调返回 (date, startTime, endTime)，date 为 "yyyy-MM-dd"，
 * 其中 endTime = startTime + suggestedDurationMinutes。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDateTimePickerDialog(
    onConfirm: (date: String, startTime: Int, endTime: Int) -> Unit,
    onDismiss: () -> Unit,
    initialDate: String,
    today: String,
    suggestedDurationMinutes: Int = 60,
) {
    // 当前选中的日期（字符串），点击行后弹出日历对话框修改它
    var selectedDate by remember { mutableStateOf(initialDate) }
    var showDateDialog by remember { mutableStateOf(false) }
    val dateValid = isValidScheduleDate(selectedDate, today)

    val startState = rememberTimePickerState(initialHour = 9, initialMinute = 0, is24Hour = true)

    val startMinute = startState.hour * 60 + startState.minute
    val endMinute = startMinute + suggestedDurationMinutes
    val timeValid = isValidSchedule(startMinute, endMinute)

    if (showDateDialog) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localMillisAtMidnight(selectedDate),
        )
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            // DatePickerDialog 仅支持 tonalElevation（不支持 containerColor）；归零即可脱离 surfaceTint 叠加。
            tonalElevation = 0.dp,
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedDate = millisToDateStrUtc(it) }
                        showDateDialog = false
                    },
                    modifier = Modifier.testTag("ConfirmDatePick"),
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        title = { Text("排期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 日期行：点击弹出日历
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateDialog = true }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 8.dp)
                        .testTag("ScheduleDateRow"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("日期")
                    Text(selectedDate)
                }
                if (!dateValid) {
                    Text(
                        text = "不能排到过去",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("ScheduleDateError"),
                    )
                }
                WheelTimePicker(
                    hour = startState.hour,
                    minute = startState.minute,
                    onValueChange = { h, m -> startState.hour = h; startState.minute = m },
                    modifier = Modifier.fillMaxWidth().testTag("StartTimePicker"),
                )
                // 结束时间 = 开始 + 建议时长，自动回显，无需手动输入。
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "结束",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatMinutes(endMinute),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp).testTag("ScheduleEndTimePreview"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedDate, startMinute, endMinute) },
                enabled = dateValid && timeValid,
                modifier = Modifier.testTag("ConfirmScheduleTime"),
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

/**
 * 排期时间选择对话框（仅时间，无日期）。
 *
 * @deprecated 日期是排期的必要信息，新代码请使用 [ScheduleDateTimePickerDialog]。
 */
@Deprecated("Use ScheduleDateTimePickerDialog with date", ReplaceWith("ScheduleDateTimePickerDialog"))
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleTimePickerDialog(
    onConfirm: (startTime: Int, endTime: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ScheduleDateTimePickerDialog(
        onConfirm = { _, start, end -> onConfirm(start, end) },
        onDismiss = onDismiss,
        initialDate = "2026-01-01",
        today = "2026-01-01",
        suggestedDurationMinutes = 60,
    )
}

/**
 * 排期时间是否有效：结束时间必须严格晚于开始时间。
 * 抽为纯逻辑便于 JVM 单元测试。
 */
fun isValidSchedule(startMinute: Int, endMinute: Int): Boolean {
    return endMinute > startMinute
}

/**
 * 滚轮时间选择器（竖向，类似闹钟设置）。
 *
 * 时（0–23）/ 分（0–59）两列滚轮，每列 5 项可视，选中项居中高亮 + 上下细分割线；
 * 随手势松手自动吸附最近项并通过 [onValueChange] 回写。外层容器持有 testTag "StartTimePicker"。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onValueChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentHour by rememberUpdatedState(hour)
    val currentMinute by rememberUpdatedState(minute)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WheelColumn(
            range = 0..23,
            selected = hour,
            onSelected = { onValueChange(it, currentMinute) },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        WheelColumn(
            range = 0..59,
            selected = minute,
            onSelected = { onValueChange(currentHour, it) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 单列滚轮：[range] 中 [selected] 居中显示。
 * 首尾各补 2 个空白项，让首项也能吸附到中心选中槽。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WheelColumn(
    range: IntRange,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 40.dp
    val visibleCount = 5
    val halfPaddingCount = (visibleCount - 1) / 2 // 2
    val list: List<Int> = List(halfPaddingCount) { 0 } + range.toList() + List(halfPaddingCount) { 0 }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val viewportHeightPx = itemHeightPx * visibleCount

    // 外部 selected 变化 → 滚到对应位置
    LaunchedEffect(selected) {
        val targetIndex = halfPaddingCount + (selected - range.first)
        listState.animateScrollToItem(targetIndex)
    }

    // 滚动停止时，取 viewport 中心最近项 → 吸附 + 回写
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (!inProgress) {
                    val layoutInfo = listState.layoutInfo
                    val viewportCenter = layoutInfo.viewportStartOffset +
                        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
                    val centerItem = layoutInfo.visibleItemsInfo.minByOrNull {
                        kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                    }
                    centerItem?.index?.let { idx ->
                        val realValue = idx - halfPaddingCount + range.first
                        if (realValue != selected && realValue in range) {
                            val targetIndex = halfPaddingCount + (realValue - range.first)
                            listState.animateScrollToItem(targetIndex)
                            onSelected(realValue)
                        }
                    }
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier.height(itemHeight * visibleCount),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("WheelList-${range.first}-${range.last}"),
            contentPadding = PaddingValues(vertical = itemHeight * halfPaddingCount),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(list.size) { index ->
                val realValue = index - halfPaddingCount + range.first
                val isSelected = realValue == selected
                Box(
                    modifier = Modifier
                        .height(itemHeight)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (realValue in range) {
                        Text(
                            text = realValue.toString().padStart(2, '0'),
                            style = if (isSelected) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // 选中槽上下分割线（距 viewport 顶部：中心 ± itemHeight/2）
        // 注意用 TopCenter 对齐，让 offset 从顶部算起；Center 对齐会让偏移相对中心叠加，
        // 导致两条线被推到 Box 下方并 clipping 错位。
        val centerYPx = viewportHeightPx / 2
        val dividerWidth = maxWidth.times(0.8f)
        val topDividerY = with(density) { (centerYPx - itemHeightPx / 2).toDp() }
        val bottomDividerY = with(density) { (centerYPx + itemHeightPx / 2).toDp() }
        HorizontalDivider(
            modifier = Modifier
                .width(dividerWidth)
                .align(Alignment.TopCenter)
                .offset(y = topDividerY),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        HorizontalDivider(
            modifier = Modifier
                .width(dividerWidth)
                .align(Alignment.TopCenter)
                .offset(y = bottomDividerY),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
    }
}
