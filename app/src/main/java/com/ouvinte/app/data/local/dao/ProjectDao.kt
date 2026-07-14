package com.ouvinte.app.data.local.dao

import androidx.room.*
import com.ouvinte.app.data.local.entity.ProjectEntity
import com.ouvinte.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY createdAt ASC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: Long)

    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun getSessionsForProject(projectId: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE projectId = :projectId ORDER BY createdAt ASC")
    suspend fun getSessionsForProjectOnce(projectId: Long): List<SessionEntity>

    @Query("UPDATE sessions SET projectId = :projectId WHERE id = :sessionId")
    suspend fun assignSessionToProject(sessionId: Long, projectId: Long?)

    @Query("SELECT COUNT(*) FROM sessions WHERE projectId = :projectId")
    suspend fun countSessionsInProject(projectId: Long): Int
}
