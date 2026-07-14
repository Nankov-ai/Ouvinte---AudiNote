package com.ouvinte.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioRecorder @Inject constructor(
    private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var amplitudeJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _fileSizeMb = MutableStateFlow(0f)
    val fileSizeMb: StateFlow<Float> = _fileSizeMb.asStateFlow()

    private var currentOutputFile: File? = null
    private var timerJob: Job? = null

    fun startRecording(): File {
        val outputFile = createOutputFile()
        currentOutputFile = outputFile

        mediaRecorder = createMediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(48000)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        _isRecording.value = true
        _elapsedSeconds.value = 0
        _amplitudes.value = emptyList()
        _fileSizeMb.value = 0f

        startAmplitudeSampling()
        startTimer()

        return outputFile
    }

    fun stopRecording(): File? {
        amplitudeJob?.cancel()
        timerJob?.cancel()

        mediaRecorder?.runCatching {
            stop()
            release()
        }
        mediaRecorder = null
        _isRecording.value = false

        return currentOutputFile
    }

    private fun startAmplitudeSampling() {
        amplitudeJob = scope.launch {
            val buffer = ArrayDeque<Float>(60)
            while (isActive) {
                delay(100)
                val raw = mediaRecorder?.maxAmplitude ?: 0
                val normalized = (raw / 32768f).coerceIn(0f, 1f)
                if (buffer.size >= 60) buffer.removeFirst()
                buffer.addLast(normalized)
                _amplitudes.value = buffer.toList()
                _fileSizeMb.value = (currentOutputFile?.length() ?: 0L) / (1024f * 1024f)
            }
        }
    }

    private fun startTimer() {
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                _elapsedSeconds.value++
            }
        }
    }

    private fun createOutputFile(): File {
        val dir = File(context.filesDir, "recordings").also { it.mkdirs() }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        return File(dir, "session_$timestamp.m4a")
    }

    private fun createMediaRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context)
        else
            @Suppress("DEPRECATION") MediaRecorder()
}
