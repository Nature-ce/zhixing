package com.zhixing.ui

import androidx.compose.runtime.Composable
import com.zhixing.ui.components.ZhixingInputDialog

/**
 * 创建任务对话框。
 *
 * 用户输入标题 → 确认/取消。
 * 标题状态由 composable 自己持有（局部 UI 状态），不污染 ViewModel。
 *
 * 实现委托给通用输入弹窗 [ZhixingInputDialog]（Zhixing 视觉规范）。
 */
@Composable
fun CreateTaskDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ZhixingInputDialog(
        title = "创建任务",
        label = "任务标题",
        fieldTag = "TaskTitleInput",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
