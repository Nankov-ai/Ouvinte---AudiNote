package com.ouvinte.app.presentation.questions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouvinte.app.data.repository.GeminiRepository
import com.ouvinte.app.data.repository.SessionRepository
import com.ouvinte.app.domain.model.Question
import com.ouvinte.app.domain.model.Speaker
import com.ouvinte.app.util.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuestionsUiState(
    val questions: List<Question> = emptyList(),
    val speakers: List<Speaker> = emptyList(),
    val isLoading: Boolean = false,
    val canLoadMore: Boolean = true,
    val currentBatch: Int = 1,
    val error: String? = null,
    val pdfExportPath: String? = null
)

@HiltViewModel
class QuestionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionRepository: SessionRepository,
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionsUiState())
    val uiState: StateFlow<QuestionsUiState> = _uiState.asStateFlow()

    private var sessionId: Long = -1L

    fun loadQuestions(sessionId: Long) {
        this.sessionId = sessionId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val full = sessionRepository.getFullSession(sessionId)

            if (full == null) {
                _uiState.update { it.copy(isLoading = false, error = "Sessão não encontrada.") }
                return@launch
            }

            if (full.questions.isNotEmpty()) {
                val maxBatch = full.questions.maxOf { it.batch }
                _uiState.update {
                    it.copy(
                        questions = full.questions,
                        speakers = full.speakers,
                        isLoading = false,
                        currentBatch = maxBatch,
                        canLoadMore = maxBatch < 5
                    )
                }
                return@launch
            }

            generateBatch(full.segments.joinToString("\n") { "[${it.speakerLabel}]: ${it.text}" }, full.speakers.map {
                com.ouvinte.app.data.remote.dto.SpeakerResult(it.label, it.displayName, it.isMain)
            }, 1)
        }
    }

    fun loadMoreQuestions() {
        if (_uiState.value.isLoading || !_uiState.value.canLoadMore) return
        viewModelScope.launch {
            val full = sessionRepository.getFullSession(sessionId) ?: return@launch
            val nextBatch = _uiState.value.currentBatch + 1
            val transcript = full.segments.joinToString("\n") { "[${it.speakerLabel}]: ${it.text}" }
            val speakers = full.speakers.map {
                com.ouvinte.app.data.remote.dto.SpeakerResult(it.label, it.displayName, it.isMain)
            }
            generateBatch(transcript, speakers, nextBatch)
        }
    }

    private suspend fun generateBatch(
        transcript: String,
        speakers: List<com.ouvinte.app.data.remote.dto.SpeakerResult>,
        batch: Int
    ) {
        _uiState.update { it.copy(isLoading = true) }
        geminiRepository.generateQuestions(transcript, speakers, batch)
            .onSuccess { result ->
                sessionRepository.saveQuestions(sessionId, result, batch)
                val full = sessionRepository.getFullSession(sessionId)!!
                _uiState.update {
                    it.copy(
                        questions = full.questions,
                        speakers = full.speakers,
                        isLoading = false,
                        currentBatch = batch,
                        canLoadMore = batch < 5
                    )
                }
            }
            .onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = "Erro: ${e.message}") }
            }
    }

    fun exportPdf() {
        viewModelScope.launch {
            val full = sessionRepository.getFullSession(sessionId) ?: return@launch
            val path = PdfExporter.export(context, full)
            _uiState.update { it.copy(pdfExportPath = path) }
        }
    }

    fun clearPdfPath() = _uiState.update { it.copy(pdfExportPath = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
