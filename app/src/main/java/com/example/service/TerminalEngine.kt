package com.example.service

import android.util.Log
import androidx.compose.ui.text.AnnotatedString
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
    val annotatedText: AnnotatedString = AnnotatedString(text),
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

class LineCursorBuffer {
    private val sb = StringBuilder()
    private var cursor = 0

    fun append(ch: Char) {
        if (cursor < sb.length) {
            sb.setCharAt(cursor, ch)
        } else {
            sb.append(ch)
        }
        cursor++
    }

    fun carriageReturn() {
        cursor = 0
    }

    fun toStringAndClear(): String {
        val result = sb.toString()
        sb.clear()
        cursor = 0
        return result
    }

    fun isNotEmpty(): Boolean = sb.isNotEmpty()
    fun asString(): String = sb.toString()
    fun clear() {
        sb.clear()
        cursor = 0
    }
}

class TerminalEngine(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val maxBufferLines: Int = 5000,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    companion object {
        private const val TAG = "TerminalEngine"
        const val DEFAULT_MAX_LINES = 5000
    }

    private val _state = MutableStateFlow(TerminalState())
    val state = _state.asStateFlow()

    private var activeShell: InteractiveShellSession? = null
    private var readJob: Job? = null
    private var currentHost: HostEntity? = null

    private val pendingLines = mutableListOf<TerminalLine>()
    private var lastFlushTime = 0L

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

                withContext(mainDispatcher) {
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
                withContext(mainDispatcher) {
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
            val buffer = ByteArray(8192)
            val lineBuffer = LineCursorBuffer()
            var pendingCr = false

            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    val text = String(buffer, 0, bytesRead, StandardCharsets.UTF_8)
                    for (ch in text) {
                        if (pendingCr) {
                            if (ch == '\n') {
                                val line = lineBuffer.toStringAndClear()
                                if (line.isNotEmpty()) {
                                    enqueueOutputLine(line)
                                }
                                pendingCr = false
                            } else {
                                lineBuffer.carriageReturn()
                                if (ch == '\r') {
                                    pendingCr = true
                                } else {
                                    lineBuffer.append(ch)
                                    pendingCr = false
                                }
                            }
                        } else if (ch == '\r') {
                            pendingCr = true
                        } else if (ch == '\n') {
                            val line = lineBuffer.toStringAndClear()
                            if (line.isNotEmpty()) {
                                enqueueOutputLine(line)
                            }
                        } else {
                            lineBuffer.append(ch)
                        }
                    }

                    // Flush batch if stream paused or reached threshold
                    val avail = try { inputStream.available() } catch (_: Exception) { 0 }
                    if (avail == 0 || pendingLinesCount() >= 50 || System.currentTimeMillis() - lastFlushTime >= 50L) {
                        flushPendingLines()
                    }

                    // If remaining buffer has interactive prompt
                    if (lineBuffer.isNotEmpty() && avail == 0) {
                        val currentStr = lineBuffer.asString()
                        if (currentStr.endsWith("$ ") || currentStr.endsWith("# ") || currentStr.endsWith("> ")) {
                            val prompt = AnsiParser.stripAnsi(currentStr)
                            withContext(mainDispatcher) {
                                _state.value = _state.value.copy(promptString = prompt)
                            }
                            lineBuffer.clear()
                        } else if (currentStr.endsWith(": ") || currentStr.endsWith("? ")) {
                            enqueueOutputLine(currentStr)
                            flushPendingLines()
                            lineBuffer.clear()
                        }
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    enqueueOutputLine("Session stream closed: ${e.message}", TerminalLineType.SYSTEM)
                    flushPendingLines()
                }
            } finally {
                flushPendingLines()
            }
        }
    }

    private fun pendingLinesCount(): Int {
        synchronized(pendingLines) {
            return pendingLines.size
        }
    }

    private fun enqueueOutputLine(rawText: String, type: TerminalLineType = TerminalLineType.OUTPUT) {
        val clean = AnsiParser.stripAnsi(rawText)
        val annotated = AnsiParser.parseAnsiToAnnotatedString(rawText)
        val line = TerminalLine(text = clean, annotatedText = annotated, type = type)
        synchronized(pendingLines) {
            pendingLines.add(line)
        }
    }

    private suspend fun flushPendingLines() {
        val toFlush: List<TerminalLine>
        synchronized(pendingLines) {
            if (pendingLines.isEmpty()) return
            toFlush = ArrayList(pendingLines)
            pendingLines.clear()
            lastFlushTime = System.currentTimeMillis()
        }
        withContext(mainDispatcher) {
            val currentLines = _state.value.lines
            val newLines = (currentLines + toFlush).takeLast(maxBufferLines)
            _state.value = _state.value.copy(lines = newLines)
        }
    }

    fun appendOutputLine(text: String, type: TerminalLineType = TerminalLineType.OUTPUT) {
        enqueueOutputLine(text, type)
        scope.launch(mainDispatcher) {
            flushPendingLines()
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
            lines = (_state.value.lines + inputLine).takeLast(maxBufferLines),
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

    fun setPtySize(cols: Int, rows: Int) {
        activeShell?.setPtySize(cols, rows)
    }

    fun sendBytes(bytes: ByteArray) {
        activeShell?.sendBytes(bytes)
    }

    fun sendCtrlKey(char: Char) {
        val ctrlCode = (char.uppercaseChar().code - '@'.code).toChar()
        activeShell?.sendInput(ctrlCode.toString())
    }

    fun clearConsole() {
        synchronized(pendingLines) {
            pendingLines.clear()
        }
        _state.value = _state.value.copy(lines = emptyList())
    }
}
