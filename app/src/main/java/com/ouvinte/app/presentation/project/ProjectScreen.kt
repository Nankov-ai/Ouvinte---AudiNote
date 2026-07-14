package com.ouvinte.app.presentation.project

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ouvinte.app.domain.model.Session
import com.ouvinte.app.domain.model.SessionStatus
import java.io.File
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(
    projectId: Long,
    projectName: String,
    onOpenSession: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var sessionMenu by remember { mutableStateOf<Session?>(null) }

    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }

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

    sessionMenu?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionMenu = null },
            title = { Text(session.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = { Text("O que queres fazer com esta sessão?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeSessionFromProject(session.id)
                    sessionMenu = null
                }) { Text("Remover da pasta") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id)
                    sessionMenu = null
                }) { Text("Eliminar sessão", color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(projectName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (uiState.sessions.isNotEmpty()) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                        } else {
                            IconButton(onClick = { viewModel.exportMergedPdf(projectName) }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "Exportar PDF unificado")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.sessions.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            "Pasta vazia. Move sessões para cá a partir do ecrã principal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.sessions, key = { it.id }) { session ->
                            ProjectSessionCard(
                                session = session,
                                onClick = { onOpenSession(session.id) },
                                onLongClick = { sessionMenu = session }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }

            uiState.error?.let { error ->
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
                ) { Text(error) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectSessionCard(
    session: Session,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (session.status) {
                    SessionStatus.ANALYSED -> Icons.Default.CheckCircle
                    SessionStatus.TRANSCRIBED -> Icons.Default.Description
                    SessionStatus.TRANSCRIBING, SessionStatus.ANALYSING -> Icons.Default.HourglassEmpty
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = when (session.status) {
                    SessionStatus.ANALYSED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    session.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
