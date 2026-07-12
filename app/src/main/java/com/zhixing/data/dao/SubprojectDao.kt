package com.zhixing.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.zhixing.data.entity.SubprojectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubprojectDao {
    @Insert
    suspend fun insertSubproject(subproject: SubprojectEntity): Long

    @Query("SELECT * FROM subprojects WHERE taskId = :taskId ORDER BY createdAt ASC")
    fun getSubprojectsByTaskId(taskId: Long): Flow<List<SubprojectEntity>>

    @Query("SELECT * FROM subprojects")
    fun getAllSubprojects(): Flow<List<SubprojectEntity>>

    @Query("UPDATE subprojects SET status = :status WHERE id = :id")
    suspend fun updateSubprojectStatus(id: Long, status: String)

    @Query("UPDATE subprojects SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateSubprojectStatusAndCompletedAt(id: Long, status: String, completedAt: Long?)

    @Query("UPDATE subprojects SET status = :status WHERE taskId = :taskId")
    suspend fun updateAllSubprojectsStatus(taskId: Long, status: String)

    @Query("DELETE FROM subprojects WHERE taskId = :taskId")
    suspend fun deleteSubprojectsByTaskId(taskId: Long)

    @Query("DELETE FROM subprojects WHERE id = :id")
    suspend fun deleteSubprojectById(id: Long)
}
