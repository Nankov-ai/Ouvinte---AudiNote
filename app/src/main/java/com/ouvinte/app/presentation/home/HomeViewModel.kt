package com.ouvinte.app.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouvinte.app.data.repository.ProjectRepository
import com.ouvinte.app.data.repository.SessionRepository
import com.ouvinte.app.domain.model.Project
import com.ouvinte.app.domain.model.Session
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HomeUiState(
    val ungroupedSessions: List<Session> = emptyList(),
    val projects: List<Project> = emptyList(),
    val allSessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val orphanedFiles: List<File> = emptyList(),
    val showOrphanDialog: Boolean = false,
    val importedSessionId: Long? = null,
    val showCreateProjectDialog: Boolean = false,
    val showMoveToProjectDialog: Session? = null
)

private data class LocalState(
    val orphanedFiles: List<File> = emptyList(),
    val showOrphanDialog: Boolean = false,
    val importedSessionId: Long? = null,
    val showCreateProjectDialog: Boolean = false,
    val showMoveToProjectDialog: Session? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SessionRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    private val _local = MutableStateFlow(LocalState())

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getAllSessions(),
        projectRepository.getAllProjects(),
        _local
    ) { sessions, projects, local ->
        HomeUiState(
            ungroupedSessions = sessions.filter { it.projectId == null },
            projects = projects,
            allSessions = sessions,
            isLoading = false,
            orphanedFiles = local.orphanedFiles,
            showOrphanDialog = local.showOrphanDialog,
            importedSessionId = local.importedSessionId,
            showCreateProjectDialog = local.showCreateProjectDialog,
            showMoveToProjectDialog = local.showMoveToProjectDialog
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    fun deleteSession(id: Long) {
        viewModelScope.launch { repository.deleteSession(id) }
    }

    fun showCreateProjectDialog() = _local.update { it.copy(showCreateProjectDialog = true) }
    fun dismissCreateProjectDialog() = _local.update { it.copy(showCreateProjectDialog = false) }

    fun createProject(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            projectRepository.createProject(name.trim())
            _local.update { it.copy(showCreateProjectDialog = false) }
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch { projectRepository.deleteProject(id) }
    }

    fun showMoveToProject(session: Session) = _local.update { it.copy(showMoveToProjectDialog = session) }
    fun dismissMoveToProject() = _local.update { it.copy(showMoveToProjectDialog = null) }

    fun assignSessionToProject(sessionId: Long, projectId: Long?) {
        viewModelScope.launch {
            projectRepository.assignSessionToProject(sessionId, projectId)
            _local.update { it.copy(showMoveToProjectDialog = null) }
        }
    }

    fun scanOrphanedRecordings() {
        viewModelScope.launch {
            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists()) {
                _local.update { it.copy(showOrphanDialog = true, orphanedFiles = emptyList()) }
                return@launch
            }
            val existingPaths = uiState.value.allSessions.map { it.audioFilePath }.toSet()
            val orphans = recordingsDir.listFiles()
                ?.filter { it.extension == "m4a" && it.absolutePath !in existingPaths }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
            _local.update { it.copy(orphanedFiles = orphans, showOrphanDialog = true) }
        }
    }

    fun importRecording(file: File) {
        viewModelScope.launch {
            val modified = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault()
            )
            val name = "Sessão ${modified.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}"
            val sessionId = repository.createSession(name = name, audioFilePath = file.absolutePath)
            _local.update { it.copy(showOrphanDialog = false, orphanedFiles = emptyList(), importedSessionId = sessionId) }
        }
    }

    fun dismissOrphanDialog() = _local.update { it.copy(showOrphanDialog = false) }
    fun clearImportedSession() = _local.update { it.copy(importedSessionId = null) }
}
