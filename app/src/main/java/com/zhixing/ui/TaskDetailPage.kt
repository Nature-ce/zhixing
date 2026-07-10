package com.zhixing.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.db.DatabaseProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 任务详情页。
 *
 * 展示任务标题 + 子项目列表，点击子项目标记为已完成。
 * 通过 TaskDetailViewModel 桥接 Room DAO 和 UI。
 *
 * DAO 参数可选，便于 instrumented test 注入内存数据库；默认走 DatabaseProvider。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailPage(
    taskId: Long,
    taskTitle: String,
    onBack: () -> Unit = {},
    taskDao: TaskDao? = null,
    subprojectDao: SubprojectDao? = null,
    scheduleDao: ScheduleDao? = null,
) {
    val context = LocalContext.current
    val db = DatabaseProvider.db(context)
    val factory = TaskDetailViewModelFactory(
        taskId = taskId,
        taskDao = taskDao ?: db.taskDao(),
        subprojectDao = subprojectDao ?: db.subprojectDao(),
        scheduleDao = scheduleDao ?: db.scheduleDao(),
    )
    val vm: TaskDetailViewModel = viewModel(factory = factory)
    val title by vm.taskTitle.collectAsState()
    val description by vm.taskDescription.collectAsState()
    val subprojects by vm.subprojects.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduleTargetId by remember { mutableStateOf(0L) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        modifier = Modifier.testTag("EditTaskButton"),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑")
                    }
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("TaskMenuButton"),
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("放弃任务") },
                            onClick = {
                                menuExpanded = false
                                pendingAction = {
                                    scope.launch {
                                        vm.abandonTask()
                                        onBack()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("AbandonTaskMenuItem"),
                        )
                        DropdownMenuItem(
                            text = { Text("删除任务") },
                            onClick = {
                                menuExpanded = false
                                pendingAction = {
                                    scope.launch {
                                        vm.deleteTask()
                                        onBack()
                                    }
                                }
                            },
                            modifier = Modifier.testTag("DeleteTaskMenuItem"),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("AddSubprojectButton"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加子项目")
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxWidth().padding(innerPadding)) {
            TaskDetailContent(
                taskTitle = title,
                taskDescription = description,
                subprojects = subprojects,
                onSubprojectClick = { id -> scope.launch { vm.changeSubprojectStatus(id, "已完成") } },
                onScheduleClick = { id ->
                    scheduleTargetId = id
                    showScheduleDialog = true
                },
                onAbandonClick = { id -> scope.launch { vm.changeSubprojectStatus(id, "已放弃") } },
            )
        }
    }

    if (showAddDialog) {
        CreateSubprojectDialog(
            onConfirm = { title ->
                vm.addSubproject(title)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showScheduleDialog) {
        ScheduleDateTimePickerDialog(
            initialDate = today(),
            today = today(),
            onConfirm = { date, start, end ->
                vm.scheduleSubproject(scheduleTargetId, date, start, end)
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false },
        )
    }

    // 编辑任务信息弹窗
    if (showEditDialog) {
        EditTaskDialog(
            initialTitle = title,
            initialDescription = description ?: "",
            onConfirm = { newTitle, newDesc ->
                scope.launch { vm.updateTaskInfo(newTitle, newDesc) }
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }

    // 放弃/删除确认弹窗
    if (pendingAction != null) {
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("确认操作") },
            text = { Text("确定要执行此操作吗？") },
            confirmButton = {
                TextButton(onClick = {
                    pendingAction?.invoke()
                    pendingAction = null
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) { Text("取消") }
            },
        )
    }
}

private fun today(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
