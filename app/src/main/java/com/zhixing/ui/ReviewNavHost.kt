package com.zhixing.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhixing.data.dao.SubprojectDao
import com.zhixing.data.dao.TaskDao

/**
 * 回顾导航图：列表 → 详情。
 *
 * 注入 DAO 便于 instrumented test 使用内存数据库。
 */
@Composable
fun ReviewNavHost(
    taskDao: TaskDao,
    subprojectDao: SubprojectDao,
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "review") {
        composable("review") {
            val factory = remember { ReviewViewModelFactory(taskDao, subprojectDao) }
            val vm: ReviewViewModel = viewModel(factory = factory)
            val items by vm.reviewItems.collectAsState()

            ReviewPage(
                taskItems = items,
                onTaskClick = { id -> navController.navigate("review/$id") },
            )
        }

        composable(
            route = "review/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getLong("taskId") ?: 0L
            val factory = remember(taskId) { ReviewDetailViewModelFactory(taskId, taskDao, subprojectDao) }
            val vm: ReviewDetailViewModel = viewModel(factory = factory)
            val title by vm.taskTitle.collectAsState()
            val subprojects by vm.subprojects.collectAsState()
            val reviewText by vm.reviewText.collectAsState()

            ReviewDetailPage(
                taskTitle = title,
                subprojects = subprojects,
                reviewText = reviewText,
                onReviewChange = vm::onReviewChange,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
