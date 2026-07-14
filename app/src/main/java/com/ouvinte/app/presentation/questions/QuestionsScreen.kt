package com.ouvinte.app.presentation.questions

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ouvinte.app.domain.model.Question
import com.ouvinte.app.domain.model.QuestionType
import com.ouvinte.app.domain.model.Speaker
import com.ouvinte.app.presentation.session.speakerColors
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionsScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: QuestionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(sessionId) { viewModel.loadQuestions(sessionId) }

    LaunchedEffect(uiState.pdfExportPath) {
        uiState.pdfExportPath?.let { path ->
            val file = File(path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Abrir PDF"))
            viewModel.clearPdfPath()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perguntas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportPdf() }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading && uiState.questions.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("A gerar perguntas…")
                    }
                }
                else -> {
                    val speakerColorMap = uiState.speakers.mapIndexed { i, s ->
                        s.label to speakerColors[i % speakerColors.size]
                    }.toMap()

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (uiState.speakers.size > 1) {
                            uiState.speakers.forEach { speaker ->
                                val speakerQuestions = uiState.questions.filter {
                                    it.speakerLabel == speaker.displayName
                                }
                                if (speakerQuestions.isNotEmpty()) {
                                    item {
                                        SpeakerHeader(
                                            speaker = speaker,
                                            color = speakerColorMap[speaker.label]
                                                ?: MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    items(speakerQuestions, key = { it.id }) { q ->
                                        QuestionCard(question = q)
                                    }
                                }
                            }
                        } else {
                            items(uiState.questions, key = { it.id }) { q ->
                                QuestionCard(question = q)
                            }
                        }

                        item {
                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator() }
                            } else if (uiState.canLoadMore) {
                                OutlinedButton(
                                    onClick = { viewModel.loadMoreQuestions() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("+ 3 Perguntas")
                                }
                            }
                        }

                        item { Spacer(Modifier.height(32.dp)) }
                    }
                }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) { Text("OK") }
                    }
                ) { Text(error) }
            }
        }
    }
}

@Composable
private fun SpeakerHeader(speaker: Speaker, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = speaker.label.take(1),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Text(
            text = speaker.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun QuestionCard(question: Question) {
    val (typeLabel, typeColor) = when (question.type) {
        QuestionType.CHALLENGE -> "Desafio" to Color(0xFFFF6B6B)
        QuestionType.FUTURE -> "Futuro" to Color(0xFF82B1FF)
        QuestionType.DEEPEN -> "Aprofundamento" to Color(0xFF80CBC4)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.QuestionMark,
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(16.dp)
                )
                SuggestionChip(
                    onClick = {},
                    label = { Text(typeLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = typeColor.copy(alpha = 0.15f),
                        labelColor = typeColor
                    )
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
