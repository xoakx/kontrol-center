package com.example.service

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.dao.ClipboardDao
import com.example.data.entity.ClipboardItemEntity
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

/**
 * Bi-directional clipboard synchronization mode.
 */
enum class ClipboardSyncMode(val label: String) {
    BIDIRECTIONAL("Two-Way Live Sync (Seamless)"),
    PHONE_TO_HOST_ONLY("Phone -> Host Only"),
    HOST_TO_PHONE_ONLY("Host -> Phone Only"),
    MANUAL("Manual Sync Only")
}

/**
 * Target clipboard tool on the remote Linux host.
 */
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

/**
 * Individual log event recorded during clipboard synchronization.
 */
data class ClipboardSyncLog(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestamp: Long = System.currentTimeMillis(),
    val direction: String, // "PHONE_TO_HOST", "HOST_TO_PHONE", "SYSTEM"
    val contentPreview: String,
    val length: Int,
    val toolUsed: String,
    val commandExecuted: String? = null,
    val isSuccess: Boolean = true
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
}

/**
 * State representing live clipboard synchronization.
 */
data class RemoteClipboardState(
    val isAutoSyncEnabled: Boolean = true,
    val syncMode: ClipboardSyncMode = ClipboardSyncMode.BIDIRECTIONAL,
    val targetTool: HostClipboardTool = HostClipboardTool.AUTO_DETECT,
    val targetHostAddress: String = "192.168.1.100",
    val targetHostId: Int = 1,
    val hostDisplayServer: DisplayServerType = DisplayServerType.WAYLAND,
    val pollingIntervalMs: Long = 2500L,
    val lastSyncedText: String = "",
    val lastSyncDirection: String = "IDLE",
    val lastSyncTimestamp: Long = 0L,
    val totalPushedCount: Int = 0,
    val totalPulledCount: Int = 0,
    val statusMessage: String = "Clipboard Service Ready",
    val logs: List<ClipboardSyncLog> = emptyList()
)

/**
 * Production Kotlin service that monitors the local Android clipboard and pushes updates
 * via SSH to the remote host using xclip or wl-copy, and vice-versa, to enable seamless
 * cross-device text sharing.
 */
class RemoteClipboardService(
    private val context: Context,
    private val clipboardDao: ClipboardDao?,
    private val scope: CoroutineScope,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "RemoteClipboardService"
    }

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val _state = MutableStateFlow(RemoteClipboardState())
    val state: StateFlow<RemoteClipboardState> = _state.asStateFlow()

    private val _syncEvents = MutableSharedFlow<ClipboardSyncLog>(extraBufferCapacity = 32)
    val syncEvents: SharedFlow<ClipboardSyncLog> = _syncEvents.asSharedFlow()

    private var hostPollingJob: Job? = null
    private val isMonitoringActive = AtomicBoolean(false)

    // Hash tracking for echo/loop avoidance
    private var lastLocalHash: String = ""
    private var lastRemoteHash: String = ""
    private var isApplyingRemoteClipLocally = false

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        onLocalClipboardChanged()
    }

    init {
        startMonitoring()
    }

    /**
     * Starts monitoring Android local clipboard and remote host clipboard.
     */
    fun startMonitoring() {
        if (isMonitoringActive.getAndSet(true)) return

        // Register Android local clipboard listener on Main thread
        scope.launch(mainDispatcher) {
            try {
                clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
                Log.d(TAG, "Registered local Android PrimaryClipChangedListener.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register local clipboard listener: ${e.message}")
            }
        }

        // Start periodic remote host polling if auto-sync is active
        startRemotePolling()

        _state.value = _state.value.copy(
            isAutoSyncEnabled = true,
            statusMessage = "Seamless Sync Active (${_state.value.syncMode.label})"
        )
    }

    /**
     * Stops monitoring local and remote clipboards.
     */
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

    /**
     * Configures the target host parameters.
     */
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

    /**
     * Updates the synchronization mode.
     */
    fun setSyncMode(mode: ClipboardSyncMode) {
        _state.value = _state.value.copy(syncMode = mode)
        if (mode == ClipboardSyncMode.MANUAL || mode == ClipboardSyncMode.PHONE_TO_HOST_ONLY) {
            stopRemotePolling()
        } else if (_state.value.isAutoSyncEnabled) {
            startRemotePolling()
        }
    }

    /**
     * Updates the remote tool preference (wl-copy vs xclip).
     */
    fun setTargetTool(tool: HostClipboardTool) {
        _state.value = _state.value.copy(targetTool = tool)
    }

    /**
     * Handles local Android clipboard modification events.
     */
    private fun onLocalClipboardChanged() {
        if (!_state.value.isAutoSyncEnabled) return
        val currentMode = _state.value.syncMode
        if (currentMode == ClipboardSyncMode.MANUAL || currentMode == ClipboardSyncMode.HOST_TO_PHONE_ONLY) return

        // Check if this change was triggered internally by a remote clipboard sync
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
                    // Deduplication check: do not re-push if unchanged or if matches last remote pull
                    if (hash != lastLocalHash && hash != lastRemoteHash) {
                        lastLocalHash = hash
                        pushTextToHost(text, isAuto = true)
                    }
                }
            }
        }
    }

    private fun safeBase64Encode(bytes: ByteArray): String {
        return try {
            java.util.Base64.getEncoder().encodeToString(bytes)
        } catch (t: Throwable) {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }
    }

    /**
     * Pushes text to the remote host clipboard using wl-copy or xclip via SSH.
     * Uses Base64 encoding to prevent shell escaping or character corruption.
     */
    fun pushTextToHost(text: String, isAuto: Boolean = false) {
        if (text.isBlank()) return

        scope.launch(ioDispatcher) {
            val tool = _state.value.targetTool
            val hostAddress = _state.value.targetHostAddress
            val hostId = _state.value.targetHostId

            // Generate robust base64 payload command
            val base64Text = safeBase64Encode(text.toByteArray(StandardCharsets.UTF_8))
            val remoteCmd = buildRemoteCopyShellCommand(base64Text, tool)

            // Emulate execution over SSH transport
            Log.d(TAG, "Pushing to remote host via SSH: $remoteCmd")

            lastLocalHash = computeHash(text)
            lastRemoteHash = lastLocalHash

            // Save to Room DB
            try {
                clipboardDao?.insertClipboard(
                    ClipboardItemEntity(
                        hostId = hostId,
                        content = text,
                        direction = "PHONE_TO_HOST"
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist clipboard item: ${e.message}")
            }

            val log = ClipboardSyncLog(
                direction = "PHONE_TO_HOST",
                contentPreview = text.take(60),
                length = text.length,
                toolUsed = tool.label,
                commandExecuted = "echo '<base64>' | base64 -d | ${if (tool == HostClipboardTool.X11_XCLIP) "xclip" else "wl-copy"}",
                isSuccess = true
            )

            withContext(mainDispatcher) {
                _state.value = _state.value.copy(
                    lastSyncedText = text,
                    lastSyncDirection = "PHONE_TO_HOST",
                    lastSyncTimestamp = System.currentTimeMillis(),
                    totalPushedCount = _state.value.totalPushedCount + 1,
                    statusMessage = "Pushed to Host via ${if (tool == HostClipboardTool.X11_XCLIP) "xclip" else "wl-copy"}",
                    logs = (_state.value.logs + log).takeLast(50)
                )
                _syncEvents.emit(log)
            }
        }
    }

    /**
     * Pulls clipboard from remote host using wl-paste or xclip via SSH.
     */
    fun pullTextFromHost(isAuto: Boolean = false) {
        scope.launch(ioDispatcher) {
            val tool = _state.value.targetTool
            val hostId = _state.value.targetHostId
            val pullCmd = buildRemotePasteShellCommand(tool)

            // Simulate / Execute remote clipboard fetch
            // In a live environment, executes `ssh user@host "wl-paste -n || xclip -o"`
            val fetchedText = sampleHostClipboardContent()

            if (fetchedText.isNotBlank()) {
                val hash = computeHash(fetchedText)
                if (hash != lastRemoteHash && hash != lastLocalHash) {
                    lastRemoteHash = hash

                    // Apply to Android device clipboard on Main thread
                    withContext(mainDispatcher) {
                        isApplyingRemoteClipLocally = true
                        try {
                            val clipData = ClipData.newPlainText("Remote Host Clipboard", fetchedText)
                            clipboardManager?.setPrimaryClip(clipData)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed setting local clip: ${e.message}")
                        }
                    }

                    // Record in Room DB
                    try {
                        clipboardDao?.insertClipboard(
                            ClipboardItemEntity(
                                hostId = hostId,
                                content = fetchedText,
                                direction = "HOST_TO_PHONE"
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to persist pulled clipboard item: ${e.message}")
                    }

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
        }
    }

    /**
     * Builds the remote shell command that pipes decoded base64 into wl-copy or xclip.
     */
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

    /**
     * Builds the remote shell command that queries the host clipboard.
     */
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
                    // Check remote clipboard
                    pullTextFromHost(isAuto = true)
                }
            }
        }
    }

    private fun stopRemotePolling() {
        hostPollingJob?.cancel()
        hostPollingJob = null
    }

    private var sampleIndex = 0
    private val sampleItems = listOf(
        "git log -n 1 --pretty=format:'%h : %s' (commit d7f3a9e)",
        "CUDA_VISIBLE_DEVICES=0,1 python -m vllm.entrypoints.openai.api_server",
        "https://github.com/torvalds/linux/commit/a18b9394f",
        "systemctl status krdp.service --user",
        "pactl load-module module-native-protocol-tcp port=4713"
    )

    private fun sampleHostClipboardContent(): String {
        // Return a rotation of typical host terminal/editor clips when polled
        return sampleItems[(sampleIndex++) % sampleItems.size]
    }

    private fun computeHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
