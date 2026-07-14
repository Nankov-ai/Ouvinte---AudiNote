package com.ouvinte.app.presentation.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouvinte.app.audio.AudioRecorder
import com.ouvinte.app.audio.RecordingService
import com.ouvinte.app.data.repository.GeminiRepository
import com.ouvinte.app.data.repository.ProjectRepository
import com.ouvinte.app.data.repository.SessionRepository
import com.ouvinte.app.domain.model.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val processingMessage: String = "A processar áudio…",
    val elapsedSeconds: Int = 0,
    val totalElapsedSeconds: Int = 0,
    val amplitudes: List<Float> = emptyList(),
    val fileSizeMb: Float = 0f,
    val splitCount: Int = 0,
    val isSplitting: Boolean = false,
    val showMaxDurationAlert: Boolean = false,
    val error: String? = null,
    val finishedSessionId: Long? = null,
    val finishedProjectId: Long? = null
)

@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioRecorder: AudioRecorder,
    private val geminiRepository: GeminiRepository,
    private val sessionRepository: SessionRepository,
    private val projectRepository: ProjectRepository
) : ViewModel() {

    companion object {
        private const val SPLIT_THRESHOLD_MB = 17f
        private const val MAX_TOTAL_SECONDS = 3 * 3600 // 3 horas
    }

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private var currentAudioFile: File? = null
    private var currentSessionId: Long = -1L
    private var transcriptionJob: Job? = null
    private var splitProjectId: Long? = null
    private var totalElapsedBeforeSplit: Int = 0
    private val splitMutex = Mutex()

    init {
        viewModelScope.launch {
            audioRecorder.amplitudes.collect { amps ->
                _uiState.update { it.copy(amplitudes = amps) }
            }
        }
        viewModelScope.launch {
            audioRecorder.elapsedSeconds.collect { secs ->
                val total = totalElapsedBeforeSplit + secs
                _uiState.update { it.copy(elapsedSeconds = secs, totalElapsedSeconds = total) }
            }
        }
        viewModelScope.launch {
            audioRecorder.fileSizeMb.collect { mb ->
                _uiState.update { it.copy(fileSizeMb = mb) }
                if (mb >= SPLIT_THRESHOLD_MB && _uiState.value.isRecording && !_uiState.value.isSplitting) {
                    autoSplit()
                }
            }
        }
    }

    private fun autoSplit() {
        viewModelScope.launch {
            splitMutex.withLock {
                if (!_uiState.value.isRecording || _uiState.value.isSplitting) return@withLock
                _uiState.update { it.copy(isSplitting = true) }

                val splitDuration = audioRecorder.elapsedSeconds.value
                totalElapsedBeforeSplit += splitDuration

                // Parar gravação actual silenciosamente
                audioRecorder.stopRecording()
                if (currentSessionId >= 0) {
                    sessionRepository.updateSessionStatus(currentSessionId, SessionStatus.RECORDED)
                    sessionRepository.updateRecordingInfo(currentSessionId, splitDuration, 0)
                }

                // Criar projecto na primeira divisão
                if (splitProjectId == null) {
                    val projectName = "Gravação ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}"
                    splitProjectId = projectRepository.createProject(projectName)
                }
                splitProjectId?.let { pid ->
                    projectRepository.assignSessionToProject(currentSessionId, pid)
                }

                // Verificar limite de 3 horas
                if (totalElapsedBeforeSplit >= MAX_TOTAL_SECONDS) {
                    context.stopService(Intent(context, RecordingService::class.java))
                    _uiState.update { it.copy(isRecording = false, isSplitting = false, showMaxDurationAlert = true) }
                    return@withLock
                }

                // Iniciar nova gravação imediatamente
                val newFile = audioRecorder.startRecording()
                currentAudioFile = newFile
                val sessionName = "Sessão ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))}"
                currentSessionId = sessionRepository.createSession(name = sessionName, audioFilePath = newFile.absolutePath)
                splitProjectId?.let { pid ->
                    projectRepository.assignSessionToProject(currentSessionId, pid)
                }

                _uiState.update { it.copy(isSplitting = false, splitCount = it.splitCount + 1) }
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            try {
                ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
                val file = audioRecorder.startRecording()
                currentAudioFile = file

                val sessionName = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                currentSessionId = sessionRepository.createSession(
                    name = "Sessão $sessionName",
                    audioFilePath = file.absolutePath
                )

                _uiState.update { it.copy(isRecording = true, error = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Erro ao iniciar gravação: ${e.message}") }
            }
        }
    }

    fun stopRecording() {
        transcriptionJob = viewModelScope.launch {
            runCatching { context.stopService(Intent(context, RecordingService::class.java)) }
            val file = audioRecorder.stopRecording()
            val splitDuration = audioRecorder.elapsedSeconds.value

            _uiState.update { it.copy(isRecording = false, isProcessing = true) }

            if (file == null || currentSessionId < 0) {
                _uiState.update { it.copy(isProcessing = false, error = "Ficheiro de gravação não encontrado.") }
                return@launch
            }

            // Se houve auto-splits, guardar última parte e navegar para projecto
            if (splitProjectId != null) {
                sessionRepository.updateSessionStatus(currentSessionId, SessionStatus.RECORDED)
                sessionRepository.updateRecordingInfo(currentSessionId, splitDuration, 0)
                projectRepository.assignSessionToProject(currentSessionId, splitProjectId!!)
                _uiState.update { it.copy(isProcessing = false, finishedProjectId = splitProjectId) }
                return@launch
            }

            // Fluxo normal — transcrição imediata
            sessionRepository.updateSessionStatus(currentSessionId, SessionStatus.TRANSCRIBING)
            geminiRepository.transcribeAudio(
                audioFile = file,
                onProgress = { message -> _uiState.update { it.copy(processingMessage = message) } },
                onFileUploaded = { uri, name -> sessionRepository.saveGeminiFile(currentSessionId, uri, name) }
            ).onSuccess { result ->
                sessionRepository.saveTranscriptionResult(currentSessionId, result)
                sessionRepository.updateRecordingInfo(
                    id = currentSessionId,
                    durationSeconds = splitDuration,
                    speakerCount = result.speakers.size
                )
                _uiState.update { it.copy(isProcessing = false, finishedSessionId = currentSessionId) }
            }.onFailure { e ->
                sessionRepository.updateSessionStatus(currentSessionId, SessionStatus.RECORDED)
                _uiState.update { it.copy(isProcessing = false, error = "Erro na transcrição: ${e.message}") }
            }
        }
    }

    fun cancelProcessing() {
        transcriptionJob?.cancel()
        transcriptionJob = null
        val idToDelete = currentSessionId
        if (idToDelete >= 0) {
            viewModelScope.launch {
                sessionRepository.deleteSession(idToDelete)
            }
        }
        _uiState.update { RecordingUiState() }
        currentSessionId = -1L
        currentAudioFile = null
    }

    fun dismissMaxDurationAlert() = _uiState.update { it.copy(showMaxDurationAlert = false) }
    fun clearError() = _uiState.update { it.copy(error = null) }
}
