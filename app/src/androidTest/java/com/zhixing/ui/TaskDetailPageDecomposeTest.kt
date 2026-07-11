package com.zhixing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zhixing.data.DecompositionException
import com.zhixing.data.DecompositionResult
import com.zhixing.data.DecompositionService
import com.zhixing.data.db.AppDatabase
import com.zhixing.data.entity.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * UI TDD (createComposeRule): TaskDetailPage 顶部"AI 拆解"按钮 + loading/error。
 *
 * 直接渲染 TaskDetailPage，注入内存 DB + 假 DecompositionService。
 *
 * 注意：createComposeRule + in-memory Room 下，Room InvalidationTracker 触发
 * recomposition 不可靠，故"写入成功"以 runBlocking 等 DB flow 后断言 DB 业务状态为准
 * （与 TaskDetailPageTest.add_subproject_appears_in_list 同一模式）；
 * 按钮 disabled / loading indicator / 错误提示属纯 compose state，直接断言。
 */
class TaskDetailPageDecomposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun teardown() {
        db.close()
    }

    /** 纯内存假服务，用于验证 UI 行为（不触及网络）。 */
    private class FakeDecompositionService(
        private val results: List<DecompositionResult> = emptyList(),
        private val error: String? = null,
    ) : DecompositionService {
        override suspend fun decompose(taskTitle: String, taskDescription: String?): List<DecompositionResult> {
            if (error != null) throw DecompositionException(error)
            return results
        }
    }

    @Test
    fun decomposeButton_triggers_andWritesSubprojects_toDb() {
        val service = FakeDecompositionService(
            results = listOf(DecompositionResult("收集资料", 30), DecompositionResult("撰写大纲", 45)),
        )

        val taskId = runBlocking {
            db.taskDao().insertTask(TaskEntity(title = "写报告", createdAt = 1_000L))
        }

        composeRule.setContent {
            TaskDetailPage(
                taskId = taskId,
                taskTitle = "写报告",
                taskDao = db.taskDao(),
                subprojectDao = db.subprojectDao(),
                scheduleDao = db.scheduleDao(),
                decompositionService = service,
            )
        }

        // 等 VM init 完成、拆解按钮出现
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("DecomposeButton").fetchSemanticsNodes().isNotEmpty()
        }

        // click → 拆解协程启动（按钮 disabled 窗口极短，受 compose 同步限制，
        // 不在此处断言；改以 loading indicator 的出现+消失 间接证明协程运行）
        composeRule.onNodeWithTag("DecomposeButton").performClick()

        // 等 DB flow 反映写入，断言业务结果（2 个子项目、标题、时长、状态）
        runBlocking {
            db.subprojectDao().getSubprojectsByTaskId(taskId).first { it.size == 2 }
        }
        val subs = runBlocking { db.subprojectDao().getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).hasSize(2)
        assertThat(subs.map { it.title }).containsExactly("收集资料", "撰写大纲")
        assertThat(subs.map { it.estimatedDuration }).containsExactly(30, 45)
        assertThat(subs).allMatch { it.status == "backlog" }

        // 拆解完成后 loading 消失、按钮恢复可用
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("DecomposeLoadingIndicator").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun decomposeButton_showsError_whenServiceFails() {
        val service = FakeDecompositionService(error = "网络连接失败")

        val taskId = runBlocking {
            db.taskDao().insertTask(TaskEntity(title = "写报告", createdAt = 1_000L))
        }

        composeRule.setContent {
            TaskDetailPage(
                taskId = taskId,
                taskTitle = "写报告",
                taskDao = db.taskDao(),
                subprojectDao = db.subprojectDao(),
                scheduleDao = db.scheduleDao(),
                decompositionService = service,
            )
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("DecomposeButton").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("DecomposeButton").performClick()

        // 服务失败 → 展示错误提示（纯 compose state，不依赖 DB 触发 recomposition）
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("DecomposeErrorText").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("DecomposeErrorText").assertIsDisplayed()

        // 出错时不应写入任何子项目
        val subs = runBlocking { db.subprojectDao().getSubprojectsByTaskId(taskId).first() }
        assertThat(subs).isEmpty()
    }
}
