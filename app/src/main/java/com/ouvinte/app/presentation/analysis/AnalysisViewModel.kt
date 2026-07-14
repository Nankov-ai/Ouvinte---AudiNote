package com.ouvinte.app.presentation.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouvinte.app.data.repository.GeminiRepository
import com.ouvinte.app.data.repository.SearchRepository
import com.ouvinte.app.data.repository.SessionRepository
import com.ouvinte.app.domain.model.Analysis
import com.ouvinte.app.domain.model.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AnalysisUiState(
    val analysis: Analysis? = null,
    val isLoading: Boolean = false,
    val loadingStep: String = "",
    val error: String? = null,
    val canShowQuestions: Boolean = false
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val geminiRepository: GeminiRepository,
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    fun loadOrAnalyse(sessionId: Long) {
        viewModelScope.launch {
            val full = sessionRepository.getFullSession(sessionId) ?: return@launch

            // Already analysed — just show it
            if (full.analysis != null) {
                _uiState.update {
                    it.copy(analysis = full.analysis, canShowQuestions = true)
                }
                return@launch
            }

            // Run analysis pipeline
            _uiState.update { it.copy(isLoading = true, loadingStep = "A extrair tópicos…") }
            sessionRepository.updateSessionStatus(sessionId, SessionStatus.ANALYSING)

            val fullTranscript = full.segments.joinToString("\n") {
                "[${it.speakerLabel}]: ${it.text}"
            }

            val topics = geminiRepository.extractTopics(fullTranscript)
                .getOrElse { listOf("palestra", "apresentação") }

            _uiState.update { it.copy(loadingStep = "A pesquisar fontes na web…") }

            val searchResults = searchRepository.searchTopics(topics)
                .getOrElse { emptyList() }

            _uiState.update { it.copy(loadingStep = "A analisar e verificar factos…") }

            geminiRepository.analyseTranscript(fullTranscript, searchResults)
                .onSuccess { result ->
                    sessionRepository.saveAnalysisResult(sessionId, result)
                    val saved = sessionRepository.getFullSession(sessionId)?.analysis
                    _uiState.update {
                        it.copy(
                            analysis = saved,
                            isLoading = false,
                            canShowQuestions = true
                        )
                    }
                }
                .onFailure { e ->
                    sessionRepository.updateSessionStatus(sessionId, SessionStatus.TRANSCRIBED)
                    _uiState.update {
                        it.copy(isLoading = false, error = "Erro na análise: ${e.message}")
                    }
                }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}
