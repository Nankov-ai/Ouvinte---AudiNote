package com.ouvinte.app.presentation.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Transcribe
import com.ouvinte.app.domain.model.Question
import com.ouvinte.app.domain.model.QuestionType
import com.ouvinte.app.domain.model.SessionStatus
import com.ouvinte.app.domain.model.TranscriptSegment

val speakerColors = listOf(
    Color(0xFF82B1FF), Color(0xFF80CBC4), Color(0xFFCEB4FB),
    Color(0xFFFFC107), Color(0xFFFF8A65), Color(0xFFA5D6A7)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    sessionId: Long,
    onAnalyse: () -> Unit,
    onBack: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var fileUriInput by remember { mutableStateOf("") }

    LaunchedEffect(sessionId) { viewModel.loadSession(sessionId) }

    if (uiState.showFileUriDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissFileUriDialog() },
            title = { Text("Usar ficheiro Gemini existente") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "O limite de uploads diário foi atingido. Introduz o nome do ficheiro já carregado anteriormente (ex: files/92x0d20emomh).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = fileUriInput,
                        onValueChange = { fileUriInput = it },
                        label = { Text("Nome do ficheiro") },
                        placeholder = { Text("files/xxxxxxxxxx") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (fileUriInput.startsWith("files/")) {
                            viewModel.retryWithFileUri(fileUriInput.trim())
                            fileUriInput = ""
                        }
                    },
                    enabled = fileUriInput.startsWith("files/")
                ) { Text("Transcrever") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissFileUriDialog() }) { Text("Cancelar") }
            }
        )
    }

    LaunchedEffect(uiState.pdfExportPath) {
        uiState.pdfExportPath?.let { path ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", File(path))
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
                title = { Text(uiState.fullSession?.session?.name ?: "Sessão") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    uiState.fullSession?.session?.audioFilePath?.let { path ->
                        val audioFile = java.io.File(path)
                        if (audioFile.exists()) {
                            IconButton(onClick = {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.provider", audioFile
                                )
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "audio/mp4"
                                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, "Partilhar áudio"))
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Partilhar áudio")
                            }
                        }
                    }
                    if (uiState.fullSession?.segments?.isNotEmpty() == true) {
                        IconButton(onClick = { viewModel.shareSession(context) }) {
                            Icon(Icons.Default.Share, contentDescription = "Partilhar")
                        }
                        IconButton(onClick = { viewModel.exportPdf() }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF")
                        }
                    }
                }
            )
        },
        bottomBar = {
            uiState.fullSession?.let { full ->
                Surface(shadowElevation = 8.dp) {
                    when {
                        uiState.isRetrying -> Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(uiState.retryMessage, style = MaterialTheme.typography.bodySmall)
                        }
                        full.session.status == SessionStatus.RECORDED && full.segments.isEmpty() ->
                            Button(
                                onClick = { viewModel.retryTranscription() },
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Tentar novamente")
                            }
                        full.segments.isNotEmpty() ->
                            Button(
                                onClick = onAnalyse,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                enabled = full.analysis == null
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (full.analysis != null) "Análise já realizada" else "Análise")
                            }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tabs só aparecem quando há conteúdo
            if (uiState.fullSession?.segments?.isNotEmpty() == true) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Transcrição") },
                        icon = { Icon(Icons.Default.Transcribe, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Perguntas") },
                        icon = { Icon(Icons.Default.QuestionAnswer, contentDescription = null) }
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.fullSession == null -> Text(
                    "Sessão não encontrada.",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> {
                    val full = uiState.fullSession!!
                    val speakerColorMap = full.speakers.mapIndexed { i, s ->
                        s.label to speakerColors[i % speakerColors.size]
                    }.toMap()

                    if (selectedTab == 1) {
                        // Tab Perguntas
                        QuestionsTabContent(
                            questions = full.questions,
                            speakers = full.speakers.map { it.displayName },
                            onGoToAnalysis = onAnalyse
                        )
                    } else {
                    // Tab Transcrição
                    Column {
                        if (full.segments.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.isTranslating) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("A traduzir…", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    OutlinedButton(
                                        onClick = { viewModel.toggleTranslation() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            if (uiState.showTranslation) "Ver original" else "Traduzir para PT",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }
                        SelectionContainer {
                            LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (full.segments.isEmpty()) {
                                    item {
                                        Text(
                                            "Transcrição não disponível.",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(32.dp)
                                        )
                                    }
                                } else {
                                    items(full.segments.size) { i ->
                                        val segment = full.segments[i]
                                        val displayText = if (uiState.showTranslation)
                                            uiState.translatedTexts?.getOrNull(i) ?: segment.text
                                        else segment.text
                                        TranscriptSegmentItem(
                                            segment = segment.copy(text = displayText),
                                            color = speakerColorMap[segment.speakerLabel]
                                                ?: MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                item { Spacer(Modifier.height(80.dp)) }
                            }
                        }
                    }
                    } // fim else tab transcrição
                }
            }
            } // fim Box
        }
    }
}

@Composable
private fun QuestionsTabContent(
    questions: List<Question>,
    speakers: List<String>,
    onGoToAnalysis: () -> Unit
) {
    if (questions.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.QuestionAnswer,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Ainda não foram geradas perguntas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGoToAnalysis) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Gerar via Análise")
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(questions, key = { it.id }) { question ->
                QuestionTabItem(question = question)
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun QuestionTabItem(question: Question) {
    val (typeLabel, typeColor) = when (question.type) {
        QuestionType.CHALLENGE -> "Desafio" to Color(0xFFFF6B6B)
        QuestionType.FUTURE    -> "Futuro"  to Color(0xFF82B1FF)
        QuestionType.DEEPEN    -> "Aprofundamento" to Color(0xFF80CBC4)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            question.speakerLabel?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
            }
            SuggestionChip(
                onClick = {},
                label = { Text(typeLabel, style = MaterialTheme.typography.labelSmall) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = typeColor.copy(alpha = 0.15f),
                    labelColor = typeColor
                )
            )
            Spacer(Modifier.height(6.dp))
            SelectionContainer {
                Text(question.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TranscriptSegmentItem(
    segment: TranscriptSegment,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = segment.speakerLabel.take(1),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Orador ${segment.speakerLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = color
                )
                Text(
                    text = formatTimestamp(segment.timestampSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = segment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatTimestamp(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
