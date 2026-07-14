package com.ouvinte.app.presentation.pin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ouvinte.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PinMode { SETUP_ENTER, SETUP_CONFIRM, UNLOCK }

data class PinUiState(
    val mode: PinMode = PinMode.UNLOCK,
    val digits: String = "",
    val error: String? = null,
    val isSuccess: Boolean = false,
    val firstPin: String = ""
)

@HiltViewModel
class PinViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PinUiState())
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    fun init(isSetup: Boolean) {
        _uiState.update { it.copy(mode = if (isSetup) PinMode.SETUP_ENTER else PinMode.UNLOCK) }
    }

    fun addDigit(digit: String) {
        val current = _uiState.value.digits
        if (current.length >= 4) return
        val updated = current + digit
        _uiState.update { it.copy(digits = updated, error = null) }
        if (updated.length == 4) onPinComplete(updated)
    }

    fun deleteDigit() {
        val current = _uiState.value.digits
        if (current.isNotEmpty()) _uiState.update { it.copy(digits = current.dropLast(1), error = null) }
    }

    private fun onPinComplete(pin: String) {
        viewModelScope.launch {
            when (_uiState.value.mode) {
                PinMode.SETUP_ENTER -> {
                    _uiState.update { it.copy(firstPin = pin, digits = "", mode = PinMode.SETUP_CONFIRM) }
                }
                PinMode.SETUP_CONFIRM -> {
                    if (pin == _uiState.value.firstPin) {
                        settings.setPin(pin)
                        _uiState.update { it.copy(isSuccess = true) }
                    } else {
                        _uiState.update { it.copy(digits = "", error = "PINs não coincidem. Tenta novamente.", mode = PinMode.SETUP_ENTER, firstPin = "") }
                    }
                }
                PinMode.UNLOCK -> {
                    val correct = settings.verifyPin(pin)
                    if (correct) {
                        _uiState.update { it.copy(isSuccess = true) }
                    } else {
                        _uiState.update { it.copy(digits = "", error = "PIN incorreto. Tenta novamente.") }
                    }
                }
            }
        }
    }
}
