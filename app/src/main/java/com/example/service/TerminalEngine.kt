package com.example.service

import android.util.Log
import com.example.data.entity.HostEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.StandardCharsets

data class TerminalLine(
    val id: Long = System.nanoTime(),
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT
)

enum class TerminalLineType {
    INPUT,
    OUTPUT,
    SYSTEM,
    ERROR,
    SUCCESS
}

data class TerminalState(
    val lines: List<TerminalLine> = emptyList(),
    val currentInput: String = "",
    val isExecuting: Boolean = false,
    val isConnected: Boolean = false,
    val connectedHost: String = "Disconnected",
    val workingDirectory: String = "~",
    val promptString: String = "$ ",
    val commandHistory: List<String> = emptyList()
)

class TerminalEngine(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "TerminalEngine"
    }

    private val _state = MutableStateFlow(TerminalState())
    val state = _state.asStateFlow()

    private var activeShell: InteractiveShellSession? = null
    private var readJob: Job? = null
    private var currentHost: HostEntity? = null

    init {
        val initialBanner = listOf(
            TerminalLine(text = "Kontrol Center Interactive PTY Shell", type = TerminalLineType.SYSTEM),
            TerminalLine(text = "Select a host to attach real SSH terminal stream.", type = TerminalLineType.SYSTEM)
        )
        _state.value = _state.value.copy(lines = initialBanner)
    }

    fun attachHost(host: HostEntity, overridePassword: String? = null) {
        currentHost = host
        connectToHost(host, overridePassword)
    }

    fun connectToHost(host: HostEntity, overridePassword: String? = null) {
        readJob?.cancel()
        activeShell?.close()

        val connectMsg = TerminalLine(
            text = "Connecting to ${host.username}@${host.address}:${host.sshPort}...",
            type = TerminalLineType.SYSTEM
        )
        _state.value = _state.value.copy(
            lines = _state.value.lines + connectMsg,
            isExecuting = true,
            connectedHost = "${host.name} (${host.address})"
        )

        scope.launch(ioDispatcher) {
            try {
                val shell = SshConnectionManager.openInteractiveShell(host, cols = 100, rows = 35, overridePassword = overridePassword)
                activeShell = shell

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isConnected = true,
                        isExecuting = false,
                        lines = _state.value.lines + TerminalLine(
                            text = "Connected! Interactive PTY session active.",
                            type = TerminalLineType.SUCCESS
                        )
                    )
                }

                startReadingShellOutput(shell.inputStream)
            } catch (e: Exception) {
                Log.e(TAG, "SSH connection error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isConnected = false,
                        isExecuting = false,
                        lines = _state.value.lines + TerminalLine(
                            text = "SSH Connection Failed: ${e.message ?: "Unknown error"}",
                            type = TerminalLineType.ERROR
                        )
                    )
                }
            }
        }
    }

    private fun startReadingShellOutput(inputStream: InputStream) {
        readJob = scope.launch(ioDispatcher) {
            val buffer = ByteArray(4096)
            val lineBuffer = StringBuilder()

            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    val text = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                    // Process incoming characters
                    for (ch in text) {
                        if (ch == '\n') {
                            val cleanLine = stripAnsiCodes(lineBuffer.toString())
                            if (cleanLine.isNotEmpty()) {
                                appendOutputLine(cleanLine)
                            }
                            lineBuffer.clear()
                        } else if (ch != '\r') {
                            lineBuffer.append(ch)
                        }
                    }

                    // If remaining buffer has prompt or interactive text
                    if (lineBuffer.isNotEmpty() && (lineBuffer.endsWith("$ ") || lineBuffer.endsWith("# ") || lineBuffer.endsWith("> "))) {
                        val prompt = stripAnsiCodes(lineBuffer.toString())
                        withContext(Dispatchers.Main) {
                            _state.value = _state.value.copy(promptString = prompt)
                        }
                        lineBuffer.clear()
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    appendOutputLine("Session stream closed: ${e.message}", TerminalLineType.SYSTEM)
                }
            }
        }
    }

    private fun stripAnsiCodes(str: String): String {
        return str.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "")
    }

    private fun appendOutputLine(text: String, type: TerminalLineType = TerminalLineType.OUTPUT) {
        scope.launch(Dispatchers.Main) {
            val line = TerminalLine(text = text, type = type)
            _state.value = _state.value.copy(
                lines = (_state.value.lines + line).takeLast(1000)
            )
        }
    }

    fun updateInput(input: String) {
        _state.value = _state.value.copy(currentInput = input)
    }

    fun appendQuickKey(key: String) {
        val shell = activeShell
        when (key) {
            "CLEAR" -> clearConsole()
            "TAB" -> shell?.sendInput("\t")
            "ESC" -> shell?.sendInput("\u001b")
            "CTRL+C" -> {
                shell?.sendInput("\u0003")
                appendOutputLine("^C", TerminalLineType.ERROR)
            }
            "UP" -> shell?.sendInput("\u001b[A")
            "DOWN" -> shell?.sendInput("\u001b[B")
            "LEFT" -> shell?.sendInput("\u001b[D")
            "RIGHT" -> shell?.sendInput("\u001b[C")
            else -> {
                _state.value = _state.value.copy(currentInput = _state.value.currentInput + key)
            }
        }
    }

    fun executeCommand(rawCommand: String? = null) {
        val cmd = (rawCommand ?: _state.value.currentInput)
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        val inputLine = TerminalLine(text = "${_state.value.promptString}$trimmed", type = TerminalLineType.INPUT)
        _state.value = _state.value.copy(
            lines = _state.value.lines + inputLine,
            currentInput = "",
            commandHistory = _state.value.commandHistory + trimmed
        )

        val shell = activeShell
        if (shell != null && shell.isConnected) {
            shell.sendInput("$trimmed\n")
        } else {
            // If shell is disconnected, attempt one-off fallback via exec channel
            val host = currentHost
            if (host != null) {
                scope.launch(ioDispatcher) {
                    try {
                        val res = SshConnectionManager.executeCommand(host, trimmed)
                        if (res.stdout.isNotEmpty()) {
                            res.stdout.lines().forEach { appendOutputLine(it) }
                        }
                        if (res.stderr.isNotEmpty()) {
                            res.stderr.lines().forEach { appendOutputLine(it, TerminalLineType.ERROR) }
                        }
                    } catch (e: Exception) {
                        appendOutputLine("Command execution failed: ${e.message}", TerminalLineType.ERROR)
                    }
                }
            } else {
                appendOutputLine("No active SSH session. Select or connect to a host first.", TerminalLineType.ERROR)
            }
        }
    }

    fun clearConsole() {
        _state.value = _state.value.copy(lines = emptyList())
    }
}
