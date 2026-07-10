package com.zhixing.ui

import com.zhixing.data.entity.ScheduleEntity
import com.zhixing.data.entity.SubprojectEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * 排期时间有效性判定纯逻辑测试。
 */
class ScheduleTimeValidatorTest {

    @Test
    fun end_greater_than_start_is_valid() {
        assertThat(isValidSchedule(startMinute = 540, endMinute = 600)).isTrue()
    }

    @Test
    fun end_equal_to_start_is_invalid() {
        assertThat(isValidSchedule(startMinute = 600, endMinute = 600)).isFalse()
    }

    @Test
    fun end_less_than_start_is_invalid() {
        assertThat(isValidSchedule(startMinute = 660, endMinute = 600)).isFalse()
    }

    @Test
    fun one_minute_gap_is_valid() {
        assertThat(isValidSchedule(startMinute = 600, endMinute = 601)).isTrue()
    }
}

/**
 * TDD (RED->GREEN): ScheduleListComposer 纯逻辑。
 *
 * 验证：
 *   - 把 ScheduleEntity + SubprojectEntity 组装成 ScheduleItem（含子项目标题）
 *   - 按 startTime 升序排列
 *   - 找不到对应子项目时标题为空字符串
 */
class ScheduleListComposerTest {

    @Test
    fun assembles_schedule_items_with_subproject_titles() {
        val scheduleItems = listOf(
            ScheduleEntity(id = 1, subprojectId = 10, date = "2026-07-08", startTime = 600, endTime = 660, createdAt = 1_000L),
            ScheduleEntity(id = 2, subprojectId = 20, date = "2026-07-08", startTime = 540, endTime = 600, createdAt = 2_000L),
        )
        val subprojects = listOf(
            SubprojectEntity(id = 10, taskId = 1, title = "选书目", status = "已完成"),
            SubprojectEntity(id = 20, taskId = 1, title = "划重点", status = "已排期"),
        )

        val result = ScheduleListComposer.assemble(scheduleItems, subprojects)

        // 按 startTime 升序：划重点(540) 在前，选书目(600) 在后
        assertThat(result).hasSize(2)
        assertThat(result[0].subprojectTitle).isEqualTo("划重点")
        assertThat(result[0].startTime).isEqualTo(540)
        assertThat(result[0].endTime).isEqualTo(600)
        assertThat(result[0].subprojectStatus).isEqualTo("已排期")
        assertThat(result[1].subprojectTitle).isEqualTo("选书目")
        assertThat(result[1].startTime).isEqualTo(600)
        assertThat(result[1].endTime).isEqualTo(660)
        assertThat(result[1].subprojectStatus).isEqualTo("已完成")
    }

    @Test
    fun missing_subproject_title_becomes_empty_string() {
        val scheduleItems = listOf(
            ScheduleEntity(id = 1, subprojectId = 99, date = "2026-07-08", startTime = 600, endTime = 660),
        )
        val subprojects = emptyList<SubprojectEntity>()

        val result = ScheduleListComposer.assemble(scheduleItems, subprojects)

        assertThat(result).hasSize(1)
        assertThat(result[0].subprojectTitle).isEqualTo("")
    }

    @Test
    fun marks_items_as_overdue_when_endTime_before_current_time_today() {
        // 今天 2026-07-08，当前时间 12:00（720 分钟）
        // endTime=660（11:00）< 720 → 逾期
        val scheduleItems = listOf(
            ScheduleEntity(id = 1, subprojectId = 10, date = "2026-07-08", startTime = 600, endTime = 660),
            // endTime=780（13:00）> 720 → 未逾期
            ScheduleEntity(id = 2, subprojectId = 20, date = "2026-07-08", startTime = 720, endTime = 780),
        )
        val subprojects = listOf(
            SubprojectEntity(id = 10, taskId = 1, title = "过期项", status = "已排期"),
            SubprojectEntity(id = 20, taskId = 1, title = "未来项", status = "已排期"),
        )

        val result = ScheduleListComposer.assemble(scheduleItems, subprojects, currentTime = 720, today = "2026-07-08")

        // 排序后：过期项(endTime=660) 在前，未来项(endTime=780) 在后
        assertThat(result[0].subprojectTitle).isEqualTo("过期项")
        assertThat(result[0].isOverdue).isTrue()
        assertThat(result[1].subprojectTitle).isEqualTo("未来项")
        assertThat(result[1].isOverdue).isFalse()
    }

    @Test
    fun items_on_past_dates_are_overdue() {
        // 昨天 2026-07-07 的任何时间都是逾期
        val scheduleItems = listOf(
            ScheduleEntity(id = 1, subprojectId = 10, date = "2026-07-07", startTime = 1200, endTime = 1260),
        )
        val subprojects = listOf(SubprojectEntity(id = 10, taskId = 1, title = "昨日", status = "已排期"))

        val result = ScheduleListComposer.assemble(scheduleItems, subprojects, currentTime = 720, today = "2026-07-08")

        assertThat(result[0].isOverdue).isTrue()
    }
}
