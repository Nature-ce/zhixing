package com.zhixing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhixing.ai.ServiceProvider
import com.zhixing.ui.components.ZhixingTopAppBar
import com.zhixing.data.DecompositionException
import com.zhixing.data.DecompositionService
import com.zhixing.data.ai.SettingsStoreFactory
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao
import com.zhixing.data.db.DatabaseProvider
import kotlinx.coroutines.CancellationException
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
    decompositionService: DecompositionService? = null,
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

    // 拆解服务装配点：测试注入 fake。
    // 生产侧的 service 采用 lazy 构造——不在组合期强制构造 Retrofit，避免 baseUrl
    // 为空/非法时页面直接崩溃（Retrofit.Builder.baseUrl 对空串抛异常）；
    // 点击"AI 拆解"时才构造，异常走现有错误提示分支展示给用户。
    val productionStore = remember { SettingsStoreFactory.create(context) }

    var decomposing by remember { mutableStateOf(false) }
    var decomposeError by remember { mutableStateOf<String?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ZhixingTopAppBar(
                title = title,
                onBack = onBack,
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
                        // 钉死暖白容器 + tonalElevation=0，脱离 DropdownMenu 默认 3dp surfaceTint 叠加，杜绝淡蓝紫味。
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
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
            // AI 拆解入口已下沉到 TaskDetailContent 内部，位于标题/描述之后、
            // 子项目列表之前，避免悬在标题上方造成阅读断裂。
            // 子项目的排期/完成/放弃操作已移至日程栏；任务栏只展示 + 创建子项目。
            TaskDetailContent(
                taskTitle = title,
                taskDescription = description,
                subprojects = subprojects,
                decomposing = decomposing,
                decomposeError = decomposeError,
                onDecompose = {
                    decomposeError = null
                    decomposing = true
                    scope.launch {
                        try {
                            // 懒构造：点击才装配 Retrofit 服务。空/非法配置在这里抛异常，
                            // 被下方 catch 捕获后展示错误提示，而非在组合期就让页面崩溃。
                            val svc = decompositionService
                                ?: ServiceProvider.decomposition(productionStore)
                            vm.decompose(svc, replaceExisting = true)
                            decomposeError = null
                        } catch (e: CancellationException) {
                            // 协程取消（如页面离开）不应被当作错误处理，恢复状态即可
                            throw e
                        } catch (e: Exception) {
                            // 兜底：service/VM 任一层未预期的异常都不该让 app 闪退，
                            // 而是展示错误提示
                            decomposeError = e.message ?: "拆解失败"
                        } finally {
                            decomposing = false
                        }
                    }
                },
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
            // 钉死暖白容器 + tonalElevation=0，脱离 M3 AlertDialog 默认 6dp surfaceTint 叠加，杜绝淡蓝紫味。
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
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
