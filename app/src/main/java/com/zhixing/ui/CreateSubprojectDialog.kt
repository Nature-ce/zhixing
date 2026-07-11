package com.zhixing.ui

import androidx.compose.runtime.Composable
import com.zhixing.ui.components.ZhixingInputDialog

/**
 * 添加子项目对话框。
 *
 * 用户输入标题 → 确认/取消。
 * 确认后调用方插入 backlog 状态子项目。
 *
 * 实现委托给通用输入弹窗 [ZhixingInputDialog]（Zhixing 视觉规范）。
 */
@Composable
fun CreateSubprojectDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ZhixingInputDialog(
        title = "添加子项目",
        label = "子项目标题",
        fieldTag = "SubprojectTitleInput",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
