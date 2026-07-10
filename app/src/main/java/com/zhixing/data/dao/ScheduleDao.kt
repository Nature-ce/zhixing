package com.zhixing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhixing.data.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Insert
    suspend fun insertScheduleItem(item: ScheduleEntity): Long

    @Query("SELECT * FROM schedule_items WHERE date = :date ORDER BY startTime ASC")
    fun getScheduleItemsByDate(date: String): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedule_items WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC, startTime ASC")
    fun getScheduleItemsBetween(startDate: String, endDate: String): Flow<List<ScheduleEntity>>

    @Query("DELETE FROM schedule_items WHERE subprojectId = :subprojectId")
    suspend fun clearScheduleForSubproject(subprojectId: Long)

    @Query("DELETE FROM schedule_items WHERE subprojectId IN (:subprojectIds)")
    suspend fun clearScheduleForSubprojects(subprojectIds: List<Long>)
}
