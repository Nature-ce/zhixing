package com.zhixing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
                TimePicker(
                    state = startState,
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
