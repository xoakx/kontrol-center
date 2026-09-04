package com.example.service

import android.util.Log
import com.example.data.entity.HostEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

enum class HidBackendType(val displayName: String) {
    WAYLAND_YDOTOOL("Wayland (ydotool)"),
    X11_XDOTEXT("X11 (xdotool)"),
    SYSFS_UHID("Linux uinput/uhid")
}

enum class VirtualMouseButton(val id: Int, val displayName: String) {
    LEFT(1, "Left Click"),
    MIDDLE(2, "Middle Click"),
    RIGHT(3, "Right Click")
}

enum class VirtualSpecialKey(val ydotoolKey: String, val xdotoolKey: String) {
    ESCAPE("1", "Escape"),
    SUPER("125", "Super_L"),
    TAB("15", "Tab"),
    ENTER("28", "Return"),
    BACKSPACE("14", "BackSpace")
}

data class VirtualInputState(
    val isConnected: Boolean = false,
    val isInitialized: Boolean = false,
    val activeBackend: HidBackendType = HidBackendType.WAYLAND_YDOTOOL,
    val lastDispatchedCommand: String = "Idle",
    val queuedEventsCount: Int = 0,
    val targetHostAddress: String = "127.0.0.1"
)

class VirtualInputService(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "VirtualInputService"
    }

    private val _state = MutableStateFlow(VirtualInputState())
    val state: StateFlow<VirtualInputState> = _state.asStateFlow()

    private var activeHost: HostEntity? = null
    private val pendingDx = AtomicInteger(0)
    private val pendingDy = AtomicInteger(0)
    private var flushJob: Job? = null

    init {
        startFlushLoop()
    }

    fun attachHost(host: HostEntity, isWayland: Boolean = true) {
        activeHost = host
        val backend = if (isWayland) HidBackendType.WAYLAND_YDOTOOL else HidBackendType.X11_XDOTEXT
        _state.value = _state.value.copy(
            isConnected = true,
            isInitialized = true,
            targetHostAddress = host.address,
            activeBackend = backend,
            lastDispatchedCommand = "Ready to send input to ${host.address} (${backend.displayName})"
        )
    }

    fun setBackend(backend: HidBackendType) {
        _state.value = _state.value.copy(
            activeBackend = backend,
            lastDispatchedCommand = "Switched to backend: ${backend.displayName}"
        )
    }

    /**
     * Queues relative mouse movement. Aggregated and flushed at 20-30Hz to avoid SSH saturation.
     */
    fun sendMouseMove(dx: Float, dy: Float) {
        pendingDx.addAndGet(dx.toInt())
        pendingDy.addAndGet(dy.toInt())
    }

    private fun startFlushLoop() {
        flushJob?.cancel()
        flushJob = scope.launch(ioDispatcher) {
            while (isActive) {
                delay(40) // ~25Hz flush rate
                val dx = pendingDx.getAndSet(0)
                val dy = pendingDy.getAndSet(0)

                if (dx != 0 || dy != 0) {
                    val host = activeHost ?: continue
                    val cmd = when (_state.value.activeBackend) {
                        HidBackendType.WAYLAND_YDOTOOL -> "ydotool mousemove -x $dx -y $dy"
                        HidBackendType.X11_XDOTEXT -> "xdotool mousemove_relative -- $dx $dy"
                        HidBackendType.SYSFS_UHID -> "uinput-inject --rel-x $dx --rel-y $dy"
                    }

                    try {
                        SshConnectionManager.executeCommand(host, cmd, timeoutMs = 2000)
                        _state.value = _state.value.copy(lastDispatchedCommand = cmd)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to send mousemove: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Dispatches mouse click directly over SSH.
     */
    fun sendMouseButton(button: VirtualMouseButton) {
        val host = activeHost ?: return
        scope.launch(ioDispatcher) {
            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> {
                    val code = when (button) {
                        VirtualMouseButton.LEFT -> "0xC0"
                        VirtualMouseButton.RIGHT -> "0xC1"
                        VirtualMouseButton.MIDDLE -> "0xC2"
                    }
                    "ydotool click $code"
                }
                HidBackendType.X11_XDOTEXT -> {
                    val btnNum = when (button) {
                        VirtualMouseButton.LEFT -> 1
                        VirtualMouseButton.MIDDLE -> 2
                        VirtualMouseButton.RIGHT -> 3
                    }
                    "xdotool click $btnNum"
                }
                HidBackendType.SYSFS_UHID -> "uinput-inject --btn ${button.name.lowercase()}"
            }

            try {
                SshConnectionManager.executeCommand(host, cmd, timeoutMs = 2000)
                _state.value = _state.value.copy(lastDispatchedCommand = cmd)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send click: ${e.message}")
            }
        }
    }

    /**
     * Dispatches keyboard typing or key press over SSH.
     */
    fun sendKeyPress(key: String) {
        val host = activeHost ?: return
        scope.launch(ioDispatcher) {
            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> "ydotool key $key"
                HidBackendType.X11_XDOTEXT -> "xdotool key $key"
                HidBackendType.SYSFS_UHID -> "uinput-inject --key $key"
            }

            try {
                SshConnectionManager.executeCommand(host, cmd, timeoutMs = 2000)
                _state.value = _state.value.copy(lastDispatchedCommand = cmd)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key: ${e.message}")
            }
        }
    }

    /**
     * Types text string on the remote host.
     */
    fun sendText(text: String) {
        val host = activeHost ?: return
        scope.launch(ioDispatcher) {
            val escaped = text.replace("\"", "\\\"").replace("'", "\\'")
            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> "ydotool type \"$escaped\""
                HidBackendType.X11_XDOTEXT -> "xdotool type --delay 5 \"$escaped\""
                HidBackendType.SYSFS_UHID -> "uinput-inject --text \"$escaped\""
            }

            try {
                SshConnectionManager.executeCommand(host, cmd, timeoutMs = 3000)
                _state.value = _state.value.copy(lastDispatchedCommand = "Typed: ${text.take(20)}...")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send text: ${e.message}")
            }
        }
    }

    /**
     * Dispatches special keyboard shortcut (ESC, SUPER, TAB, ENTER, BACKSPACE).
     */
    fun sendSpecialKey(key: VirtualSpecialKey) {
        val host = activeHost ?: return
        scope.launch(ioDispatcher) {
            val keyArg = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> key.ydotoolKey
                HidBackendType.X11_XDOTEXT -> key.xdotoolKey
                HidBackendType.SYSFS_UHID -> key.xdotoolKey
            }
            sendKeyPress(keyArg)
        }
    }

    /**
     * Alias for typing text on the remote host.
     */
    fun sendTextInput(text: String) {
        sendText(text)
    }
}
