package com.zhixing

import android.os.Bundle
import androidx.activity.ComponentActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zhixing.data.db.DatabaseProvider
import com.zhixing.ui.DaySchedulePage
import com.zhixing.ui.DayScheduleViewModel
import com.zhixing.ui.DayScheduleViewModelFactory
import com.zhixing.ui.ReviewNavHost
import com.zhixing.ui.TaskNavHost
import com.zhixing.ui.WeekDateUtils
import com.zhixing.ui.WeekSchedulePage
import com.zhixing.ui.WeekScheduleViewModel
import com.zhixing.ui.WeekScheduleViewModelFactory
import com.zhixing.ui.theme.ZhixingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZhixingTheme {
                MainScreen()
            }
        }
    }
}

private fun today(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

private val tabs = listOf(
    Tab("tasks", "任务") { Icon(Icons.Default.CheckCircle, contentDescription = "任务") },
    Tab("schedule", "日程") { Icon(Icons.Default.DateRange, contentDescription = "日程") },
    Tab("review", "回顾") { Icon(Icons.Default.Edit, contentDescription = "回顾") },
)

@Composable
private fun MainScreen() {
    val navController = rememberNavController()
    // 日程视图模式（日 / 周）放在 MainScreen 层级，
    // 跨 tab 切换时不被销毁（NavHost 内的 composable 会被回退栈移除）。
    var isScheduleWeekView by remember { mutableStateOf(false) }
    // backlog 折叠状态同例：提到 MainScreen，切 tab 再切回不会丢失。
    var collapsedBacklog by remember { mutableStateOf(false) }
    Scaffold(
        bottomBar = {
            NavigationBar(
                // M3 NavigationBar 默认 surface + tonalElevation=3dp 叠加 surfaceTint；
                // 直接钉死暖白容器并归零 tonal，杜绝 elevation overlay 引入冷蓝紫味。
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = tab.icon,
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = { navController.navigate(tab.route) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "schedule",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            composable("tasks") { TasksTab() }
            composable("schedule") {
                ScheduleTab(
                    isWeekView = isScheduleWeekView,
                    onViewChange = { isScheduleWeekView = it },
                    collapsedBacklog = collapsedBacklog,
                    onCollapsedBacklogChange = { collapsedBacklog = it },
                )
            }
            composable("review") { ReviewTab() }
        }
    }
}

@Composable
private fun TasksTab() {
    val context = LocalContext.current
    val db = DatabaseProvider.db(context)
    TaskNavHost(
        taskDao = db.taskDao(),
        subprojectDao = db.subprojectDao(),
        scheduleDao = db.scheduleDao(),
    )
}

@Composable
private fun ScheduleTab(
    isWeekView: Boolean,
    onViewChange: (Boolean) -> Unit,
    collapsedBacklog: Boolean,
    onCollapsedBacklogChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val db = DatabaseProvider.db(context)
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部切换：日 / 周
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = !isWeekView,
                onClick = { onViewChange(false) },
                label = { Text("日") },
                modifier = Modifier.testTag("DayViewChip"),
            )
            FilterChip(
                selected = isWeekView,
                onClick = { onViewChange(true) },
                label = { Text("周") },
                modifier = Modifier.testTag("WeekViewChip"),
            )
        }

        // 视图区域（占满剩余空间）
        if (isWeekView) {
            val weekDates = WeekDateUtils.weekDatesOfDay(today())
            val factory = WeekScheduleViewModelFactory(
                weekDates = weekDates,
                scheduleDao = db.scheduleDao(),
                subprojectDao = db.subprojectDao(),
                taskDao = db.taskDao(),
            )
            val weekVm: WeekScheduleViewModel = viewModel(factory = factory)
            val itemsByDate by weekVm.itemsByDate.collectAsState()
            val backlog by weekVm.backlogItems.collectAsState()

            WeekSchedulePage(
                weekDates = weekDates,
                itemsByDate = itemsByDate,
                backlogItems = backlog,
                onScheduleSubproject = { subprojectId, date, startTime, endTime ->
                    scope.launch {
                        weekVm.scheduleSubproject(subprojectId, date, startTime, endTime)
                    }
                },
                onCompleteSubproject = { subprojectId ->
                    scope.launch { weekVm.completeSubproject(subprojectId) }
                },
                onAbandonSubproject = { subprojectId ->
                    scope.launch { weekVm.abandonSubproject(subprojectId) }
                },
                onUnscheduleSubproject = { subprojectId ->
                    scope.launch { weekVm.unscheduleSubproject(subprojectId) }
                },
                onUpdateSubproject = { subprojectId, title, estimatedDuration ->
                    scope.launch { weekVm.updateSubproject(subprojectId, title, estimatedDuration) }
                },
                collapsed = collapsedBacklog,
                onCollapsedChange = onCollapsedBacklogChange,
                modifier = Modifier.weight(1f),
            )
        } else {
            val factory = DayScheduleViewModelFactory(
                date = today(),
                taskDao = db.taskDao(),
                scheduleDao = db.scheduleDao(),
                subprojectDao = db.subprojectDao(),
            )
            val vm: DayScheduleViewModel = viewModel(factory = factory)
            val items by vm.scheduleItems.collectAsState()
            val backlog by vm.backlogItems.collectAsState()

            DaySchedulePage(
                date = today(),
                scheduleItems = items,
                backlogItems = backlog,
                onScheduleSubproject = { subprojectId, startTime, endTime ->
                    scope.launch {
                        vm.scheduleSubproject(subprojectId, today(), startTime, endTime)
                    }
                },
                onCompleteSubproject = { subprojectId ->
                    scope.launch { vm.completeSubproject(subprojectId) }
                },
                onAbandonSubproject = { subprojectId ->
                    scope.launch { vm.abandonSubproject(subprojectId) }
                },
                onUnscheduleSubproject = { subprojectId ->
                    scope.launch { vm.unscheduleSubproject(subprojectId) }
                },
                onUpdateSubproject = { subprojectId, title, estimatedDuration ->
                    scope.launch { vm.updateSubproject(subprojectId, title, estimatedDuration) }
                },
                collapsed = collapsedBacklog,
                onCollapsedChange = onCollapsedBacklogChange,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReviewTab() {
    val context = LocalContext.current
    val db = DatabaseProvider.db(context)
    ReviewNavHost(
        taskDao = db.taskDao(),
        subprojectDao = db.subprojectDao(),
    )
}

@Composable
private fun PlaceholderPage(name: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(name)
    }
}
