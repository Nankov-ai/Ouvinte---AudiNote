package com.ouvinte.app.presentation.session

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouvinte.app.data.repository.FullSession
import com.ouvinte.app.data.repository.GeminiRepository
import com.ouvinte.app.data.repository.SessionRepository
import com.ouvinte.app.domain.model.QuestionType
import com.ouvinte.app.domain.model.SessionStatus
import com.ouvinte.app.util.PdfExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SessionUiState(
    val fullSession: FullSession? = null,
    val isLoading: Boolean = true,
    val isRetrying: Boolean = false,
    val retryMessage: String = "",
    val error: String? = null,
    val pdfExportPath: String? = null,
    val translatedTexts: List<String>? = null,
    val isTranslating: Boolean = false,
    val showTranslation: Boolean = false,
    val showFileUriDialog: Boolean = false
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SessionRepository,
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionUiState())
    val uiState: StateFlow<SessionUiState> = _uiState.asStateFlow()

    private var currentSessionId: Long = -1L

    fun loadSession(sessionId: Long) {
        currentSessionId = sessionId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.getFullSession(sessionId) }
                .onSuccess { full ->
                    if (full != null
                        && full.session.status == SessionStatus.TRANSCRIBING
                        && full.segments.isEmpty()
                    ) {
                        repository.updateSessionStatus(sessionId, SessionStatus.RECORDED)
                        val recovered = repository.getFullSession(sessionId)
                        _uiState.update { it.copy(fullSession = recovered, isLoading = false) }
                    } else {
                        _uiState.update { it.copy(fullSession = full, isLoading = false) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun toggleTranslation() {
        val state = _uiState.value
        if (state.translatedTexts != null) {
            _uiState.update { it.copy(showTranslation = !it.showTranslation) }
            return
        }
        val segments = state.fullSession?.segments ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isTranslating = true) }
            geminiRepository.translateSegments(segments)
                .onSuccess { texts ->
                    _uiState.update { it.copy(translatedTexts = texts, showTranslation = true, isTranslating = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(isTranslating = false) }
                }
        }
    }

    fun retryTranscription() {
        val session = _uiState.value.fullSession?.session ?: return
        val audioFile = File(session.audioFilePath)
        if (!audioFile.exists()) {
            _uiState.update { it.copy(error = "Ficheiro de áudio não encontrado no dispositivo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRetrying = true, retryMessage = "A preparar…", error = null) }
            repository.updateSessionStatus(currentSessionId, SessionStatus.TRANSCRIBING)

            geminiRepository.transcribeAudio(
                audioFile = audioFile,
                existingFileUri = session.geminiFileUri,
                existingFileName = session.geminiFileName,
                onProgress = { message -> _uiState.update { it.copy(retryMessage = message) } },
                onFileUploaded = { uri, name -> repository.saveGeminiFile(currentSessionId, uri, name) }
            ).onSuccess { result ->
                repository.saveTranscriptionResult(currentSessionId, result)
                repository.updateRecordingInfo(currentSessionId, 0, result.speakers.size)
                val updated = repository.getFullSession(currentSessionId)
                _uiState.update { it.copy(fullSession = updated, isRetrying = false, retryMessage = "") }
            }.onFailure { e ->
                repository.updateSessionStatus(currentSessionId, SessionStatus.RECORDED)
                val is429 = e.message?.contains("429") == true
                val msg = when {
                    is429 -> "Limite de uploads atingido (quota diária). Usa um ficheiro já carregado anteriormente."
                    e.message?.contains("404") == true -> "Modelo não encontrado."
                    else -> "Erro: ${e.message}"
                }
                _uiState.update { it.copy(isRetrying = false, retryMessage = "", error = msg, showFileUriDialog = is429) }
            }
        }
    }

    fun retryWithFileUri(geminiFileName: String) {
        // geminiFileName ex: "files/92x0d20emomh"
        val session = _uiState.value.fullSession?.session ?: return
        val audioFile = File(session.audioFilePath)
        val uri = "https://generativelanguage.googleapis.com/v1beta/$geminiFileName"
        _uiState.update { it.copy(showFileUriDialog = false) }
        viewModelScope.launch {
            _uiState.update { it.copy(isRetrying = true, retryMessage = "A verificar ficheiro…", error = null) }
            repository.updateSessionStatus(currentSessionId, SessionStatus.TRANSCRIBING)
            repository.saveGeminiFile(currentSessionId, uri, geminiFileName)

            geminiRepository.transcribeAudio(
                audioFile = audioFile,
                existingFileUri = uri,
                existingFileName = geminiFileName,
                onProgress = { message -> _uiState.update { it.copy(retryMessage = message) } },
                onFileUploaded = { u, n -> repository.saveGeminiFile(currentSessionId, u, n) }
            ).onSuccess { result ->
                repository.saveTranscriptionResult(currentSessionId, result)
                repository.updateRecordingInfo(currentSessionId, 0, result.speakers.size)
                val updated = repository.getFullSession(currentSessionId)
                _uiState.update { it.copy(fullSession = updated, isRetrying = false, retryMessage = "") }
            }.onFailure { e ->
                repository.updateSessionStatus(currentSessionId, SessionStatus.RECORDED)
                _uiState.update { it.copy(isRetrying = false, retryMessage = "", error = "Erro: ${e.message}") }
            }
        }
    }

    fun dismissFileUriDialog() = _uiState.update { it.copy(showFileUriDialog = false) }

    fun exportPdf() {
        viewModelScope.launch {
            val full = repository.getFullSession(currentSessionId) ?: return@launch
            val path = PdfExporter.export(context, full)
            _uiState.update { it.copy(pdfExportPath = path) }
        }
    }

    fun shareSession(context: Context) {
        val full = _uiState.value.fullSession ?: return
        val sb = StringBuilder()
        sb.appendLine(full.session.name)
        sb.appendLine()

        if (full.segments.isNotEmpty()) {
            sb.appendLine("=== TRANSCRIÇÃO ===")
            full.segments.forEach { seg ->
                sb.appendLine("[Orador ${seg.speakerLabel}] ${seg.text}")
            }
            sb.appendLine()
        }

        full.analysis?.let { analysis ->
            sb.appendLine("=== RESUMO ===")
            sb.appendLine(analysis.summary)
            sb.appendLine()
        }

        if (full.questions.isNotEmpty()) {
            sb.appendLine("=== PERGUNTAS ===")
            full.questions.forEach { q ->
                val tipo = when (q.type) {
                    QuestionType.CHALLENGE -> "Desafio"
                    QuestionType.FUTURE    -> "Futuro"
                    QuestionType.DEEPEN    -> "Aprofundamento"
                }
                sb.appendLine("[$tipo] ${q.text}")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sb.toString())
            putExtra(Intent.EXTRA_SUBJECT, full.session.name)
        }
        context.startActivity(Intent.createChooser(intent, "Partilhar sessão"))
    }

    fun clearPdfPath() = _uiState.update { it.copy(pdfExportPath = null) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
