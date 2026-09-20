package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.entity.HostEntity
import com.example.service.TerminalEngine
import com.example.service.TerminalState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TerminalUiState(
    val isCtrlActive: Boolean = false,
    val isAltActive: Boolean = false,
    val isAutoScrollEnabled: Boolean = true,
    val fontSizeSp: Int = 12
)

class TerminalViewModel(
    val terminalEngine: TerminalEngine,
    val hostProvider: (() -> HostEntity?)? = null
) : ViewModel() {

    // Default constructor for injection or tests
    constructor() : this(TerminalEngine(CoroutineScope(Dispatchers.Main + SupervisorJob())))

    val terminalState: StateFlow<TerminalState> = terminalEngine.state

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    private val _isCtrlActive = MutableStateFlow(false)
    val isCtrlActive: StateFlow<Boolean> = _isCtrlActive.asStateFlow()

    fun attachHost(host: HostEntity, overridePassword: String? = null) {
        terminalEngine.attachHost(host, overridePassword)
    }

    fun connectToHost(host: HostEntity, overridePassword: String? = null) {
        terminalEngine.connectToHost(host, overridePassword)
    }

    fun updateInput(input: String) {
        terminalEngine.updateInput(input)
    }

    fun executeCommand(rawCommand: String? = null) {
        terminalEngine.executeCommand(rawCommand)
    }

    fun clearConsole() {
        terminalEngine.clearConsole()
    }

    fun toggleCtrlModifier() {
        _isCtrlActive.update { !it }
        _uiState.update { it.copy(isCtrlActive = _isCtrlActive.value) }
    }

    fun sendCtrlKey(char: Char) {
        terminalEngine.sendCtrlKey(char)
        _isCtrlActive.value = false
        _uiState.update { it.copy(isCtrlActive = false) }
    }

    fun sendQuickKey(key: String) {
        if (key == "CTRL") {
            toggleCtrlModifier()
            return
        }

        if (_isCtrlActive.value) {
            _isCtrlActive.value = false
            _uiState.update { it.copy(isCtrlActive = false) }

            if (key.length == 1 && key[0].isLetter()) {
                terminalEngine.sendCtrlKey(key[0])
                return
            } else if (key.equals("CTRL+C", ignoreCase = true) || key.equals("C", ignoreCase = true)) {
                terminalEngine.sendCtrlKey('C')
                return
            }
        }

        terminalEngine.appendQuickKey(key)
    }

    fun setPtySize(cols: Int, rows: Int) {
        terminalEngine.setPtySize(cols, rows)
    }

    fun setFontSize(sizeSp: Int) {
        _uiState.update { it.copy(fontSizeSp = sizeSp.coerceIn(8, 24)) }
    }

    fun toggleAutoScroll() {
        _uiState.update { it.copy(isAutoScrollEnabled = !it.isAutoScrollEnabled) }
    }
}
