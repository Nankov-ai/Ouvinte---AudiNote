package com.ouvinte.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.ouvinte.app.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TestStatus { IDLE, TESTING, OK, ERROR }

data class SettingsUiState(
    val geminiKey: String = "",
    val searchKey: String = "",
    val engineId: String = "",
    val isSaved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = true,
    val testStatus: TestStatus = TestStatus.IDLE,
    val testMessage: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val (gemini, search, engine) = settings.getStoredKeys()
            _uiState.update {
                it.copy(geminiKey = gemini, searchKey = search, engineId = engine, isLoading = false)
            }
        }
    }

    fun onGeminiKeyChange(value: String) = _uiState.update {
        it.copy(geminiKey = value, error = null, testStatus = TestStatus.IDLE)
    }
    fun onSearchKeyChange(value: String) = _uiState.update { it.copy(searchKey = value, error = null) }
    fun onEngineIdChange(value: String) = _uiState.update { it.copy(engineId = value, error = null) }

    fun save() {
        val state = _uiState.value
        if (state.geminiKey.isBlank()) {
            _uiState.update { it.copy(error = "A chave Gemini é obrigatória.") }
            return
        }
        viewModelScope.launch {
            settings.saveApiKeys(state.geminiKey, state.searchKey, state.engineId)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun testGeminiKey() {
        val key = _uiState.value.geminiKey.trim()
        if (key.isBlank()) {
            _uiState.update { it.copy(error = "Introduz a chave Gemini primeiro.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(testStatus = TestStatus.TESTING, testMessage = "A verificar…") }
            runCatching {
                withContext(Dispatchers.IO) {
                    // Usa o SDK — único método que suporta todos os formatos de chave (AIza, AQ., etc.)
                    val model = GenerativeModel(modelName = "gemini-3.5-flash", apiKey = key)
                    val response = model.generateContent(content { text("Responde apenas com OK.") })
                    response.text ?: error("Sem resposta")
                }
            }.onSuccess {
                _uiState.update { it.copy(testStatus = TestStatus.OK, testMessage = "Chave válida ✓") }
            }.onFailure { e ->
                val msg = when {
                    e.message?.contains("API_KEY_INVALID", true) == true ||
                    e.message?.contains("invalid", true) == true -> "Chave inválida."
                    e.message?.contains("403", true) == true -> "Sem permissão. Verifica a chave."
                    e.message?.contains("quota", true) == true -> "Quota excedida."
                    else -> "Erro: ${e.message?.take(120) ?: "desconhecido"}"
                }
                _uiState.update { it.copy(testStatus = TestStatus.ERROR, testMessage = msg) }
            }
        }
    }

    fun clearSaved() = _uiState.update { it.copy(isSaved = false) }
}
