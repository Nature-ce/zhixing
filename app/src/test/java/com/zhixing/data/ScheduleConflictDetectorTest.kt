package com.zhixing.data

import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import com.zhixing.ui.ScheduleItem
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * TDD: 同任务内排期时间冲突检测。
 *
 * CONTEXT.md 规则:
 *   - 把子项目拖放到已被占用的时间格子（同一任务内）→ 系统拒绝放置（提示冲突）
 *   - 把子项目拖放到已被占用的时间格子（跨任务）→ 允许重叠
 *
 * 纯领域逻辑，零 Android 依赖。
 */
class ScheduleConflictDetectorTest {

    private val subA1 = SubprojectEntity(id = 1, taskId = 100, title = "A1")
    private val subA2 = SubprojectEntity(id = 2, taskId = 100, title = "A2")
    private val subB1 = SubprojectEntity(id = 3, taskId = 200, title = "B1")
    private val subprojects = listOf(subA1, subA2, subB1)

    @Test
    fun `no conflict when day has no existing schedules`() {
        val result = ScheduleConflictDetector.hasConflict(
            subprojectId = 1,
            date = "2026-07-09",
            startTime = 540,
            endTime = 600,
            existingSchedules = emptyList(),
            subprojects = subprojects,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `no conflict when overlapping schedule belongs to different task`() {
        // B1（taskId=200）已排 9:00-10:00；A1（taskId=100）排同一时段 → 跨任务允许
        val existing = listOf(
            ScheduleEntity(id = 10, subprojectId = 3, date = "2026-07-09", startTime = 540, endTime = 600, createdAt = 1),
        )
        val result = ScheduleConflictDetector.hasConflict(
            subprojectId = 1, date = "2026-07-09", startTime = 540, endTime = 600,
            existingSchedules = existing, subprojects = subprojects,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `conflict when overlapping schedule belongs to same task`() {
        // A2（taskId=100）已排 9:00-10:00；A1（同 task）排 9:00-10:00 → 冲突
        val existing = listOf(
            ScheduleEntity(id = 10, subprojectId = 2, date = "2026-07-09", startTime = 540, endTime = 600, createdAt = 1),
        )
        val result = ScheduleConflictDetector.hasConflict(
            subprojectId = 1, date = "2026-07-09", startTime = 540, endTime = 600,
            existingSchedules = existing, subprojects = subprojects,
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `no conflict when same task schedule is on different time slot`() {
        // A2 已排 9:00-10:00；A1 排 10:00-11:00（紧邻但不重叠）→ 不冲突
        val existing = listOf(
            ScheduleEntity(id = 10, subprojectId = 2, date = "2026-07-09", startTime = 540, endTime = 600, createdAt = 1),
        )
        val result = ScheduleConflictDetector.hasConflict(
            subprojectId = 1, date = "2026-07-09", startTime = 600, endTime = 660,
            existingSchedules = existing, subprojects = subprojects,
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `conflict when new slot partially overlaps same task existing slot`() {
        // A2 已排 9:00-10:00；A1 排 9:30-10:30（9:30-10:00 重叠）→ 冲突
        val existing = listOf(
            ScheduleEntity(id = 10, subprojectId = 2, date = "2026-07-09", startTime = 540, endTime = 600, createdAt = 1),
        )
        val result = ScheduleConflictDetector.hasConflict(
            subprojectId = 1, date = "2026-07-09", startTime = 570, endTime = 630,
            existingSchedules = existing, subprojects = subprojects,
        )
        assertThat(result).isTrue()
    }

    // ---- List<ScheduleItem> 重载（面向 UI 预览层）----

    /** 用 schedule_items 的子集（已含 taskId）构造一条已排项，与现有 ScheduleEntity 测试同语义。 */
    private fun item(
        id: Long,
        subprojectId: Long,
        taskId: Long,
        start: Int,
        end: Int,
    ) = ScheduleItem(
        id = id,
        subprojectId = subprojectId,
        subprojectTitle = "",
        startTime = start,
        endTime = end,
        taskId = taskId,
    )

    @Test
    fun `item-overload same task overlap is conflict`() {
        // subprojectId=2（taskId=100）已排 9:00-10:00；subprojectId=1（同 task）排 9:00-10:00 → 冲突
        val existing = listOf(item(10, 2, 100, 540, 600))
        assertThat(
            ScheduleConflictDetector.hasConflict(
                subprojectId = 1, taskId = 100, startTime = 540, endTime = 600, existing = existing,
            ),
        ).isTrue()
    }

    @Test
    fun `item-overload different task overlap is not conflict`() {
        // taskId=200 块占 9:00-10:00；taskId=100 排同一时段 → 跨任务允许
        val existing = listOf(item(10, 3, 200, 540, 600))
        assertThat(
            ScheduleConflictDetector.hasConflict(
                subprojectId = 1, taskId = 100, startTime = 540, endTime = 600, existing = existing,
            ),
        ).isFalse()
    }

    @Test
    fun `item-overload excludes self by subprojectId`() {
        // 同一块（subprojectId=1, task 100）与自己比较 → 不冲突
        val existing = listOf(item(10, 1, 100, 540, 600))
        assertThat(
            ScheduleConflictDetector.hasConflict(
                subprojectId = 1, taskId = 100, startTime = 540, endTime = 600, existing = existing,
            ),
        ).isFalse()
    }

    @Test
    fun `item-overload adjacent slots are not conflict`() {
        // 同 task 已排 9:00-10:00；新块 10:00-11:00（紧邻）→ 不冲突
        val existing = listOf(item(10, 2, 100, 540, 600))
        assertThat(
            ScheduleConflictDetector.hasConflict(
                subprojectId = 1, taskId = 100, startTime = 600, endTime = 660, existing = existing,
            ),
        ).isFalse()
    }
}
