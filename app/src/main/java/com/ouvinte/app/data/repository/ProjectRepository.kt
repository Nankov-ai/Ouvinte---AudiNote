package com.ouvinte.app.data.repository

import com.google.gson.Gson
import com.ouvinte.app.data.local.dao.ProjectDao
import com.ouvinte.app.data.local.dao.SessionDao
import com.ouvinte.app.data.local.entity.ProjectEntity
import com.ouvinte.app.domain.model.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val sessionDao: SessionDao,
    private val gson: Gson
) {
    fun getAllProjects(): Flow<List<Project>> =
        projectDao.getAllProjects().map { it.map { e -> e.toDomain() } }

    suspend fun createProject(name: String): Long =
        projectDao.insertProject(ProjectEntity(name = name, createdAt = System.currentTimeMillis()))

    suspend fun deleteProject(id: Long) = projectDao.deleteProject(id)

    fun getSessionsForProject(projectId: Long): Flow<List<com.ouvinte.app.domain.model.Session>> =
        projectDao.getSessionsForProject(projectId).map { it.map { e -> e.toDomain() } }

    suspend fun assignSessionToProject(sessionId: Long, projectId: Long?) =
        projectDao.assignSessionToProject(sessionId, projectId)

    suspend fun countSessionsInProject(projectId: Long): Int =
        projectDao.countSessionsInProject(projectId)

    suspend fun getFullSessionsForProject(projectId: Long): List<FullSession> {
        val sessions = projectDao.getSessionsForProjectOnce(projectId).sortedBy { it.createdAt }
        return sessions.map { sessionEntity ->
            val id = sessionEntity.id
            val speakers = sessionDao.getSpeakersForSession(id)
            val segments = sessionDao.getTranscriptForSession(id)
            val analysis = sessionDao.getAnalysisForSession(id)
            val questions = sessionDao.getQuestionsForSession(id)
            FullSession(
                session = sessionEntity.toDomain(),
                speakers = speakers.map { it.toDomain() },
                segments = segments.map { it.toDomain() },
                analysis = analysis?.toDomain(gson),
                questions = questions.map { it.toDomain() }
            )
        }
    }
}

fun ProjectEntity.toDomain() = Project(
    id = id,
    name = name,
    createdAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault())
)
