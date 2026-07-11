package com.zhixing.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhixing.data.dao.ScheduleDao
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao

/**
 * 任务导航图：列表 → 详情。
 *
 * 注入 DAO 便于 instrumented test 使用内存数据库。
 */
@Composable
fun TaskNavHost(
    taskDao: TaskDao,
    subprojectDao: SubprojectDao,
    scheduleDao: ScheduleDao,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "tasks") {
        composable("tasks") {
            val factory = remember { TaskListViewModelFactory(taskDao, subprojectDao) }
            val vm: TaskListViewModel = viewModel(factory = factory)
            val items by vm.taskItems.collectAsState()
            val hasAnyTask by vm.hasAnyTask.collectAsState()

            var showDialog by remember { mutableStateOf(false) }

            // Box 让 FAB 能浮在列表之上。列表非空时才显示新增按钮——
            // 列表为空时由 ListView 内部的 EmptyState / "没有进行中的任务" 提供创建入口，
            // 避免重复按钮（符合 CONTEXT.md 第一次打开的欢迎页体验）。
            Box(modifier = Modifier.fillMaxSize()) {
                ListView(
                    taskItems = items,
                    onCreateFirstTask = { showDialog = true },
                    onTaskClick = { id -> navController.navigate("task/$id") },
                    hasAnyTask = hasAnyTask,
                )

                if (items.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { showDialog = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .testTag("AddTaskButton"),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新增任务")
                    }
                }
            }

            if (showDialog) {
                CreateTaskDialog(
                    onConfirm = { title ->
                        vm.addTask(title)
                        showDialog = false
                    },
                    onDismiss = { showDialog = false },
                )
            }
        }

        composable(
            route = "task/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: 0L

            TaskDetailPage(
                taskId = taskId,
                taskTitle = "",
                onBack = { navController.popBackStack() },
                taskDao = taskDao,
                subprojectDao = subprojectDao,
                scheduleDao = scheduleDao,
            )
        }
    }
}
