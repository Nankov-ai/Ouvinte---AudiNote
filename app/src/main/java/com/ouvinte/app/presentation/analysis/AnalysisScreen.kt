package com.ouvinte.app.presentation.analysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ouvinte.app.domain.model.Analysis
import com.ouvinte.app.domain.model.FactCheck
import com.ouvinte.app.domain.model.FactVerdict
import com.ouvinte.app.domain.model.Source

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    sessionId: Long,
    onQuestions: () -> Unit,
    onBack: () -> Unit,
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(sessionId) { viewModel.loadOrAnalyse(sessionId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análise") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState.canShowQuestions) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = onQuestions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Default.QuestionAnswer, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Perguntas")
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> LoadingState(step = uiState.loadingStep)
                uiState.analysis != null -> AnalysisContent(analysis = uiState.analysis!!)
                uiState.error != null -> ErrorState(
                    error = uiState.error!!,
                    onRetry = { viewModel.loadOrAnalyse(sessionId) }
                )
            }
        }
    }
}

@Composable
private fun LoadingState(step: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(step, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Tentar novamente") }
    }
}

@Composable
private fun AnalysisContent(analysis: Analysis) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionTitle("Resumo") }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = analysis.summary,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (analysis.factChecks.isNotEmpty()) {
            item { SectionTitle("Verificação de Factos") }
            items(analysis.factChecks) { fc -> FactCheckCard(fc) }
        }

        if (analysis.sources.isNotEmpty()) {
            item { SectionTitle("Fontes") }
            items(analysis.sources) { source -> SourceCard(source) }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun FactCheckCard(factCheck: FactCheck) {
    val (icon, color, label) = when (factCheck.verdict) {
        FactVerdict.VERIFIED -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "Verificado")
        FactVerdict.UNVERIFIED -> Triple(Icons.Default.Help, Color(0xFFFFC107), "Não Verificado")
        FactVerdict.INCORRECT -> Triple(Icons.Default.Cancel, Color(0xFFF44336), "Incorreto")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = color)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "\"${factCheck.claim}\"",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (factCheck.evidence.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = factCheck.evidence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SourceCard(source: Source) {
    val uriHandler = LocalUriHandler.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { runCatching { uriHandler.openUri(source.url) } }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp).padding(top = 2.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = source.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (source.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = source.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}
