package com.zhixing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zhixing.data.entity.SubprojectEntity

/**
 * 回顾详情页。
 *
 * 展示任务标题 + 子项目列表（含状态）+ 回顾文本输入区。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDetailPage(
    taskTitle: String,
    subprojects: List<SubprojectEntity>,
    reviewText: String,
    onReviewChange: (String) -> Unit,
    onBack: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(taskTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding).padding(16.dp)) {
            // 完成时间线进度图
            ReviewProgressChart(
                result = TimelineComposer.assemble(subprojects),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "回顾",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            OutlinedTextField(
                value = reviewText,
                onValueChange = onReviewChange,
                label = { Text("写下你的回顾...") },
                modifier = Modifier.fillMaxWidth().testTag("ReviewTextInput"),
                minLines = 4,
            )
        }
    }
}
