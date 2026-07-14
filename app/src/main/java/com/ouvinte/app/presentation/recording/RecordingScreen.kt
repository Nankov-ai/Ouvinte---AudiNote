package com.ouvinte.app.presentation.recording

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onRecordingFinished: (Long) -> Unit,
    onRecordingFinishedProject: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    LaunchedEffect(uiState.finishedSessionId) {
        uiState.finishedSessionId?.let { onRecordingFinished(it) }
    }
    LaunchedEffect(uiState.finishedProjectId) {
        uiState.finishedProjectId?.let { onRecordingFinishedProject(it) }
    }

    if (uiState.showMaxDurationAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissMaxDurationAlert() },
            title = { Text("Limite de 3 horas atingido") },
            text = { Text("A gravação foi automaticamente dividida e parada ao atingir 3 horas. Todas as partes estão agrupadas numa pasta.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissMaxDurationAlert() }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isRecording) "A Gravar" else "Nova Sessão") },
                navigationIcon = {
                    if (!uiState.isRecording && !uiState.isProcessing) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                // Waveform visualizer
                WaveformVisualizer(
                    amplitudes = uiState.amplitudes,
                    isRecording = uiState.isRecording,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                // Status text
                AnimatedContent(
                    targetState = when {
                        uiState.isProcessing -> uiState.processingMessage
                        uiState.isRecording -> "A ouvir o palestrante…"
                        else -> "Toca para começar a gravar"
                    },
                    label = "status"
                ) { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Timer
                if (uiState.isRecording || uiState.isSplitting) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDuration(uiState.totalElapsedSeconds),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 48.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState.splitCount > 0) {
                            Text(
                                text = if (uiState.isSplitting) "A iniciar nova parte…"
                                       else "Parte ${uiState.splitCount + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Record / Stop button
                if (uiState.isProcessing) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(72.dp))
                        TextButton(onClick = { viewModel.cancelProcessing() }) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    RecordButton(
                        isRecording = uiState.isRecording,
                        onClick = {
                            if (uiState.isRecording) viewModel.stopRecording()
                            else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }

                // Error snackbar
                uiState.error?.let { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordButton(isRecording: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size((72 * scale).dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = if (isRecording)
                MaterialTheme.colorScheme.error
            else
                MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
            contentDescription = if (isRecording) "Parar" else "Gravar",
            modifier = Modifier.size(36.dp)
        )
    }
}

@Composable
private fun WaveformVisualizer(
    amplitudes: List<Float>,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier.background(surfaceVariantColor, MaterialTheme.shapes.large)) {
        if (amplitudes.isEmpty()) {
            // Idle state: flat line
            drawLine(
                color = primaryColor.copy(alpha = 0.3f),
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 2.dp.toPx()
            )
            return@Canvas
        }

        val barCount = amplitudes.size
        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.6f
        val gap = (totalWidth / barCount) * 0.4f
        val centerY = size.height / 2

        amplitudes.forEachIndexed { index, amplitude ->
            val barHeight = (amplitude * size.height * 0.85f).coerceAtLeast(4.dp.toPx())
            val x = index * (barWidth + gap) + gap / 2
            val color = lerp(primaryColor.copy(alpha = 0.4f), primaryColor, amplitude)

            drawRoundRect(
                color = color,
                topLeft = Offset(x, centerY - barHeight / 2),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
