package com.zhixing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日程项实体。子项目在时间轴上的一次占用。
 *
 * 与 SubprojectEntity 是多对一：一个子项目可有多个跨天的日程占用。
 * onDelete = CASCADE：子项目被删除时，对应日程项自动删除。
 *
 * 时间表示：date 为 "yyyy-MM-dd"，startTime/endTime 为从 0 点起的分钟数。
 */
@Entity(
    tableName = "schedule_items",
    foreignKeys = [
        ForeignKey(
            entity = SubprojectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subprojectId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("subprojectId"), Index("date")],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subprojectId: Long,
    val date: String,
    val startTime: Int,
    val endTime: Int,
    val createdAt: Long = 0L,
)
