package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.dao.ClipboardDao
import com.example.data.entity.ClipboardItemEntity
import com.example.data.entity.HostEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

enum class ClipboardSyncMode(val label: String) {
    BIDIRECTIONAL("Two-Way Live Sync (Seamless)"),
    PHONE_TO_HOST_ONLY("Phone -> Host Only"),
    HOST_TO_PHONE_ONLY("Host -> Phone Only"),
    MANUAL("Manual Sync Only")
}

enum class HostClipboardTool(val label: String, val copyCmd: String, val pasteCmd: String) {
    AUTO_DETECT(
        "Auto-Detect (Wayland wl-copy / X11 xclip)",
        "if command -v wl-copy >/dev/null 2>&1 && [ -n \"\$WAYLAND_DISPLAY\" ]; then wl-copy; else xclip -selection clipboard; fi",
        "if command -v wl-paste >/dev/null 2>&1 && [ -n \"\$WAYLAND_DISPLAY\" ]; then wl-paste -n; else xclip -o -selection clipboard; fi"
    ),
    WAYLAND_WL_COPY(
        "Wayland Native (wl-copy / wl-paste)",
        "wl-copy",
        "wl-paste -n"
    ),
    X11_XCLIP(
        "X11 Native (xclip -selection clipboard)",
        "xclip -selection clipboard",
        "xclip -o -selection clipboard"
    )
}

data class ClipboardSyncLog(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String,
    val contentPreview: String,
    val length: Int,
    val toolUsed: String,
    val commandExecuted: String? = null,
    val isSuccess: Boolean = true
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
}

data class RemoteClipboardState(
    val isAutoSyncEnabled: Boolean = true,
    val syncMode: ClipboardSyncMode = ClipboardSyncMode.BIDIRECTIONAL,
    val targetTool: HostClipboardTool = HostClipboardTool.AUTO_DETECT,
    val targetHostAddress: String = "127.0.0.1",
    val targetHostId: Int = 1,
    val hostDisplayServer: DisplayServerType = DisplayServerType.WAYLAND,
    val pollingIntervalMs: Long = 3000L,
    val lastSyncedText: String = "",
    val lastSyncDirection: String = "IDLE",
    val lastSyncTimestamp: Long = 0L,
    val totalPushedCount: Int = 0,
    val totalPulledCount: Int = 0,
    val statusMessage: String = "Clipboard Service Ready",
    val logs: List<ClipboardSyncLog> = emptyList()
)

class RemoteClipboardService(
    private val context: Context,
    private val clipboardDao: ClipboardDao?,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    companion object {
        private const val TAG = "RemoteClipboardService"
    }

    private val clipboardManager: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val _state = MutableStateFlow(RemoteClipboardState())
    val state: StateFlow<RemoteClipboardState> = _state.asStateFlow()

    private val _syncEvents = MutableSharedFlow<ClipboardSyncLog>(extraBufferCapacity = 32)
    val syncEvents: SharedFlow<ClipboardSyncLog> = _syncEvents.asSharedFlow()

    private var activeHost: HostEntity? = null
    private var hostPollingJob: Job? = null
    private val isMonitoringActive = AtomicBoolean(false)
    private var isApplyingRemoteClipLocally = false

    private var lastLocalHash: String = ""
    private var lastRemoteHash: String = ""

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        onLocalClipboardChanged()
    }

    init {
        startMonitoring()
    }

    fun attachHost(host: HostEntity, displayServer: DisplayServerType = DisplayServerType.WAYLAND) {
        activeHost = host
        configureTargetHost(host.id, host.address, displayServer)
    }

    fun startMonitoring() {
        if (isMonitoringActive.getAndSet(true)) return

        scope.launch(mainDispatcher) {
            try {
                clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
                Log.d(TAG, "Registered local Android PrimaryClipChangedListener.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register local clipboard listener: ${e.message}")
            }
        }

        startRemotePolling()

        _state.value = _state.value.copy(
            isAutoSyncEnabled = true,
            statusMessage = "Seamless Sync Active (${_state.value.syncMode.label})"
        )
    }

    fun stopMonitoring() {
        isMonitoringActive.set(false)
        scope.launch(mainDispatcher) {
            try {
                clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing listener: ${e.message}")
            }
        }
        stopRemotePolling()
        _state.value = _state.value.copy(
            isAutoSyncEnabled = false,
            statusMessage = "Clipboard Sync Paused"
        )
    }

    fun configureTargetHost(
        hostId: Int,
        address: String,
        displayServer: DisplayServerType = DisplayServerType.WAYLAND
    ) {
        val matchedTool = if (displayServer == DisplayServerType.X11) {
            HostClipboardTool.X11_XCLIP
        } else {
            HostClipboardTool.WAYLAND_WL_COPY
        }

        _state.value = _state.value.copy(
            targetHostId = hostId,
            targetHostAddress = address,
            hostDisplayServer = displayServer,
            targetTool = matchedTool
        )
    }

    fun setSyncMode(mode: ClipboardSyncMode) {
        _state.value = _state.value.copy(syncMode = mode)
        if (mode == ClipboardSyncMode.MANUAL || mode == ClipboardSyncMode.PHONE_TO_HOST_ONLY) {
            stopRemotePolling()
        } else if (_state.value.isAutoSyncEnabled) {
            startRemotePolling()
        }
    }

    fun setTargetTool(tool: HostClipboardTool) {
        _state.value = _state.value.copy(targetTool = tool)
    }

    private fun onLocalClipboardChanged() {
        if (!_state.value.isAutoSyncEnabled) return
        val currentMode = _state.value.syncMode
        if (currentMode == ClipboardSyncMode.MANUAL || currentMode == ClipboardSyncMode.HOST_TO_PHONE_ONLY) return

        if (isApplyingRemoteClipLocally) {
            isApplyingRemoteClipLocally = false
            return
        }

        scope.launch(mainDispatcher) {
            val primaryClip = clipboardManager?.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                val text = primaryClip.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                if (text.isNotBlank()) {
                    val hash = computeHash(text)
                    if (hash != lastLocalHash && hash != lastRemoteHash) {
                        lastLocalHash = hash
                        pushTextToHost(text, isAuto = true)
                    }
                }
            }
        }
    }

    private fun safeBase64Encode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Pushes text to the remote host clipboard using real wl-copy or xclip via SSH.
     */
    fun pushTextToHost(text: String, isAuto: Boolean = false) {
        if (text.isBlank()) return
        val host = activeHost ?: return

        scope.launch(ioDispatcher) {
            val tool = _state.value.targetTool
            val base64Text = safeBase64Encode(text.toByteArray(StandardCharsets.UTF_8))
            val remoteCmd = buildRemoteCopyShellCommand(base64Text, tool)

            lastLocalHash = computeHash(text)
            lastRemoteHash = lastLocalHash

            try {
                val result = SshConnectionManager.executeCommand(host, remoteCmd, timeoutMs = 4000)
                val isSuccess = result.isSuccess

                try {
                    clipboardDao?.insertClipboard(
                        ClipboardItemEntity(
                            hostId = host.id,
                            content = text,
                            direction = "PHONE_TO_HOST"
                        )
                    )
                } catch (_: Exception) {}

                val log = ClipboardSyncLog(
                    direction = "PHONE_TO_HOST",
                    contentPreview = text.take(60),
                    length = text.length,
                    toolUsed = tool.label,
                    commandExecuted = remoteCmd,
                    isSuccess = isSuccess
                )

                withContext(mainDispatcher) {
                    _state.value = _state.value.copy(
                        lastSyncedText = text,
                        lastSyncDirection = "PHONE_TO_HOST",
                        lastSyncTimestamp = System.currentTimeMillis(),
                        totalPushedCount = _state.value.totalPushedCount + 1,
                        statusMessage = if (isSuccess) "Pushed to Host (${text.length} chars)" else "Push failed: ${result.stderr.take(30)}",
                        logs = (_state.value.logs + log).takeLast(50)
                    )
                    _syncEvents.emit(log)
                }
            } catch (e: Exception) {
                Log.e(TAG, "SSH push error: ${e.message}")
            }
        }
    }

    /**
     * Pulls clipboard from remote host using real wl-paste or xclip via SSH.
     */
    fun pullTextFromHost(isAuto: Boolean = false) {
        val host = activeHost ?: return

        scope.launch(ioDispatcher) {
            val tool = _state.value.targetTool
            val pullCmd = buildRemotePasteShellCommand(tool)

            try {
                val result = SshConnectionManager.executeCommand(host, pullCmd, timeoutMs = 3000)
                val fetchedText = result.stdout.trimEnd()

                if (result.isSuccess && fetchedText.isNotBlank()) {
                    val hash = computeHash(fetchedText)
                    if (hash != lastRemoteHash && hash != lastLocalHash) {
                        lastRemoteHash = hash

                        withContext(mainDispatcher) {
                            isApplyingRemoteClipLocally = true
                            try {
                                val clipData = ClipData.newPlainText("Remote Host Clipboard", fetchedText)
                                clipboardManager?.setPrimaryClip(clipData)
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed setting local clip: ${e.message}")
                            }
                        }

                        try {
                            clipboardDao?.insertClipboard(
                                ClipboardItemEntity(
                                    hostId = host.id,
                                    content = fetchedText,
                                    direction = "HOST_TO_PHONE"
                                )
                            )
                        } catch (_: Exception) {}

                        val log = ClipboardSyncLog(
                            direction = "HOST_TO_PHONE",
                            contentPreview = fetchedText.take(60),
                            length = fetchedText.length,
                            toolUsed = tool.label,
                            commandExecuted = pullCmd,
                            isSuccess = true
                        )

                        withContext(mainDispatcher) {
                            _state.value = _state.value.copy(
                                lastSyncedText = fetchedText,
                                lastSyncDirection = "HOST_TO_PHONE",
                                lastSyncTimestamp = System.currentTimeMillis(),
                                totalPulledCount = _state.value.totalPulledCount + 1,
                                statusMessage = "Pulled from Host (${fetchedText.length} chars)",
                                logs = (_state.value.logs + log).takeLast(50)
                            )
                            _syncEvents.emit(log)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pull remote clipboard: ${e.message}")
            }
        }
    }

    fun buildRemoteCopyShellCommand(base64Payload: String, tool: HostClipboardTool): String {
        return when (tool) {
            HostClipboardTool.WAYLAND_WL_COPY -> {
                "echo -n \"$base64Payload\" | base64 -d | wl-copy"
            }
            HostClipboardTool.X11_XCLIP -> {
                "echo -n \"$base64Payload\" | base64 -d | xclip -selection clipboard"
            }
            HostClipboardTool.AUTO_DETECT -> {
                "echo -n \"$base64Payload\" | base64 -d | (wl-copy 2>/dev/null || xclip -selection clipboard 2>/dev/null)"
            }
        }
    }

    fun buildRemotePasteShellCommand(tool: HostClipboardTool): String {
        return when (tool) {
            HostClipboardTool.WAYLAND_WL_COPY -> "wl-paste -n 2>/dev/null"
            HostClipboardTool.X11_XCLIP -> "xclip -o -selection clipboard 2>/dev/null"
            HostClipboardTool.AUTO_DETECT -> "wl-paste -n 2>/dev/null || xclip -o -selection clipboard 2>/dev/null"
        }
    }

    private fun startRemotePolling() {
        if (hostPollingJob?.isActive == true) return
        hostPollingJob = scope.launch(ioDispatcher) {
            while (isActive && _state.value.isAutoSyncEnabled) {
                delay(_state.value.pollingIntervalMs)
                if (_state.value.syncMode == ClipboardSyncMode.BIDIRECTIONAL ||
                    _state.value.syncMode == ClipboardSyncMode.HOST_TO_PHONE_ONLY
                ) {
                    pullTextFromHost(isAuto = true)
                }
            }
        }
    }

    private fun stopRemotePolling() {
        hostPollingJob?.cancel()
        hostPollingJob = null
    }

    private fun computeHash(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
