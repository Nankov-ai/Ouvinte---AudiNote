package com.ouvinte.app.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ouvinte.app.presentation.settings.TestStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isFirstSetup: Boolean = false,
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var geminiVisible by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            viewModel.clearSaved()
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isFirstSetup) "Configurar Chaves de API" else "Chaves de API") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (isFirstSetup) {
                Text(
                    "Para usar o Ouvinte precisas das tuas próprias chaves de API. São gratuitas para obter e ficam guardadas apenas neste dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Gemini API Key
            SectionLabel("Gemini API Key *", "Obtém em aistudio.google.com → Get API key")
            OutlinedTextField(
                value = uiState.geminiKey,
                onValueChange = viewModel::onGeminiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Gemini API Key") },
                visualTransformation = if (geminiVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { geminiVisible = !geminiVisible }) {
                        Icon(
                            if (geminiVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            // Botão testar + feedback
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::testGeminiKey,
                    enabled = uiState.testStatus != TestStatus.TESTING && uiState.geminiKey.isNotBlank()
                ) {
                    if (uiState.testStatus == TestStatus.TESTING) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("A verificar…")
                    } else {
                        Text("Testar chave Gemini")
                    }
                }
                when (uiState.testStatus) {
                    TestStatus.OK -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                    TestStatus.ERROR -> Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    else -> {}
                }
            }
            if (uiState.testStatus != TestStatus.IDLE && uiState.testStatus != TestStatus.TESTING) {
                Text(
                    uiState.testMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.testStatus == TestStatus.OK) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.error
                )
            }

            // Google Search API Key
            SectionLabel("Google Search API Key (opcional)", "Google Cloud Console → Credenciais → Criar chave de API")
            OutlinedTextField(
                value = uiState.searchKey,
                onValueChange = viewModel::onSearchKeyChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Google Search API Key") },
                visualTransformation = if (searchVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { searchVisible = !searchVisible }) {
                        Icon(
                            if (searchVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            // Search Engine ID
            SectionLabel("Search Engine ID (opcional)", "programmablesearchengine.google.com → copia o valor do cx=")
            OutlinedTextField(
                value = uiState.engineId,
                onValueChange = viewModel::onEngineIdChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Engine ID") },
                singleLine = true
            )

            uiState.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                enabled = !uiState.isLoading
            ) {
                Text("Guardar e continuar")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
