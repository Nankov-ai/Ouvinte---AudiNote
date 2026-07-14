package com.ouvinte.app.presentation.project

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouvinte.app.data.repository.ProjectRepository
import com.ouvinte.app.data.repository.SessionRepository
import com.ouvinte.app.domain.model.Project
import com.ouvinte.app.domain.model.Session
import com.ouvinte.app.util.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ProjectUiState(
    val project: Project? = null,
    val sessions: List<Session> = emptyList(),
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val pdfExportPath: String? = null,
    val error: String? = null
)

@HiltViewModel
class ProjectViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    private var currentProjectId: Long = -1L

    fun loadProject(projectId: Long) {
        currentProjectId = projectId
        viewModelScope.launch {
            projectRepository.getSessionsForProject(projectId).collect { sessions ->
                _uiState.update { it.copy(sessions = sessions, isLoading = false) }
            }
        }
    }

    fun removeSessionFromProject(sessionId: Long) {
        viewModelScope.launch {
            projectRepository.assignSessionToProject(sessionId, null)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { sessionRepository.deleteSession(sessionId) }
    }

    fun exportMergedPdf(projectName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            runCatching {
                val fullSessions = projectRepository.getFullSessionsForProject(currentProjectId)
                PdfExporter.exportProject(context, projectName, fullSessions)
            }.onSuccess { path ->
                _uiState.update { it.copy(isExporting = false, pdfExportPath = path) }
            }.onFailure { e ->
                _uiState.update { it.copy(isExporting = false, error = "Erro ao exportar: ${e.message}") }
            }
        }
    }

    fun clearPdfPath() = _uiState.update { it.copy(pdfExportPath = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
