package com.ouvinte.app.presentation.home

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ouvinte.app.domain.model.Project
import com.ouvinte.app.domain.model.Session
import com.ouvinte.app.domain.model.SessionStatus
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNewSession: () -> Unit,
    onOpenSession: (Long) -> Unit,
    onOpenProject: (Long, String) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var projectToDelete by remember { mutableStateOf<Project?>(null) }

    LaunchedEffect(uiState.importedSessionId) {
        uiState.importedSessionId?.let { id ->
            viewModel.clearImportedSession()
            onOpenSession(id)
        }
    }

    // Create project dialog
    if (uiState.showCreateProjectDialog) {
        var projectName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { viewModel.dismissCreateProjectDialog() },
            title = { Text("Nova pasta") },
            text = {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Nome da pasta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.createProject(projectName) },
                    enabled = projectName.isNotBlank()
                ) { Text("Criar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCreateProjectDialog() }) { Text("Cancelar") }
            }
        )
    }

    // Move session to project dialog
    uiState.showMoveToProjectDialog?.let { session ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMoveToProject() },
            title = { Text("Mover para pasta") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.projects.isEmpty()) {
                        Text("Não tens pastas criadas.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        uiState.projects.forEach { project ->
                            OutlinedCard(
                                onClick = { viewModel.assignSessionToProject(session.id, project.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Text(project.name, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        if (session.projectId != null) {
                            TextButton(
                                onClick = { viewModel.assignSessionToProject(session.id, null) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Remover de pasta", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissMoveToProject() }) { Text("Cancelar") }
            }
        )
    }

    // Delete session dialog
    sessionToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Eliminar sessão") },
            text = { Text("Tens a certeza que queres eliminar \"${session.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id)
                    sessionToDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Delete project dialog
    projectToDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectToDelete = null },
            title = { Text("Eliminar pasta") },
            text = { Text("Eliminar \"${project.name}\"? As sessões dentro ficam sem pasta.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProject(project.id)
                    projectToDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { projectToDelete = null }) { Text("Cancelar") }
            }
        )
    }

    // Orphan recordings dialog
    if (uiState.showOrphanDialog) {
        OrphanRecordingsDialog(
            files = uiState.orphanedFiles,
            onImport = { viewModel.importRecording(it) },
            onDismiss = { viewModel.dismissOrphanDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ouvinte") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { viewModel.showCreateProjectDialog() }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Nova pasta")
                    }
                    IconButton(onClick = { viewModel.scanOrphanedRecordings() }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Recuperar gravações")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Definições")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSession,
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                text = { Text("Nova Sessão") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.projects.isEmpty() && uiState.ungroupedSessions.isEmpty() ->
                    EmptyState(modifier = Modifier.align(Alignment.Center))
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Projects section
                        if (uiState.projects.isNotEmpty()) {
                            item {
                                Text(
                                    "Pastas",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(uiState.projects, key = { "project_${it.id}" }) { project ->
                                ProjectCard(
                                    project = project,
                                    sessionCount = uiState.allSessions.count { it.projectId == project.id },
                                    onClick = { onOpenProject(project.id, project.name) },
                                    onLongClick = { projectToDelete = project }
                                )
                            }
                            if (uiState.ungroupedSessions.isNotEmpty()) {
                                item {
                                    Text(
                                        "Sessões",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                    )
                                }
                            }
                        }

                        // Ungrouped sessions
                        items(uiState.ungroupedSessions, key = { "session_${it.id}" }) { session ->
                            SessionCard(
                                session = session,
                                onClick = { onOpenSession(session.id) },
                                onLongClick = { sessionToDelete = session },
                                onMoveToProject = if (uiState.projects.isNotEmpty()) {
                                    { viewModel.showMoveToProject(session) }
                                } else null
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    project: Project,
    sessionCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$sessionCount sessão(ões)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: Session,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveToProject: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    if (onMoveToProject != null) showMenu = true else onLongClick()
                }
            ),
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
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = session.createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (session.durationSeconds > 0) {
                        Text(
                            text = formatDuration(session.durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (session.speakerCount > 0) {
                        Text(
                            text = "${session.speakerCount} orador(es)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            onMoveToProject?.let {
                DropdownMenuItem(
                    text = { Text("Mover para pasta") },
                    leadingIcon = { Icon(Icons.Default.DriveFileMove, contentDescription = null) },
                    onClick = { showMenu = false; it() }
                )
            }
            DropdownMenuItem(
                text = { Text("Eliminar", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { showMenu = false; onLongClick() }
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Text(
            text = "Nenhuma sessão gravada ainda.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Toca no microfone para começar.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
private fun OrphanRecordingsDialog(
    files: List<File>,
    onImport: (File) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
        title = { Text(if (files.isEmpty()) "Nenhuma gravação encontrada" else "Gravações recuperáveis") },
        text = {
            if (files.isEmpty()) {
                Text("Não foram encontrados ficheiros de áudio sem sessão correspondente.")
            } else {
                androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files) { file ->
                        val modified = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault()
                        )
                        val sizeKb = file.length() / 1024
                        val sizeText = if (sizeKb >= 1024) "${sizeKb / 1024}MB" else "${sizeKb}KB"
                        OutlinedCard(
                            onClick = { onImport(file) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(modified.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), style = MaterialTheme.typography.bodyMedium)
                                    Text(sizeText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.Add, contentDescription = "Importar")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } }
    )
}
