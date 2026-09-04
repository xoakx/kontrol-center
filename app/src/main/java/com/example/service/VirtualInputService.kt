package com.example.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

enum class VirtualSpecialKey(val waylandKey: String, val x11Key: String) {
    ESCAPE("1", "Escape"),
    ENTER("28", "Return"),
    TAB("15", "Tab"),
    BACKSPACE("14", "BackSpace"),
    SUPER("125", "Super_L"),
    UP("103", "Up"),
    DOWN("108", "Down"),
    LEFT("105", "Left"),
    RIGHT("106", "Right")
}

data class VirtualInputState(
    val isConnected: Boolean = true,
    val isInitialized: Boolean = true,
    val activeBackend: HidBackendType = HidBackendType.WAYLAND_YDOTOOL,
    val lastDispatchedCommand: String = "ydotool daemon active",
    val queuedEventsCount: Int = 0,
    val targetHostAddress: String = "127.0.0.1"
)

/**
 * VirtualInputService captures touch, cursor, and keyboard interactions
 * and generates corresponding low-latency HID commands for Linux hosts
 * (supporting Wayland via ydotool/ydotoold, and X11 via xdotool).
 */
class VirtualInputService(
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(VirtualInputState())
    val state: StateFlow<VirtualInputState> = _state.asStateFlow()

    fun setBackend(backend: HidBackendType) {
        _state.value = _state.value.copy(
            activeBackend = backend,
            lastDispatchedCommand = "Switched to backend: ${backend.displayName}"
        )
    }

    fun configureTargetHost(hostAddress: String, isWayland: Boolean) {
        val backend = if (isWayland) HidBackendType.WAYLAND_YDOTOOL else HidBackendType.X11_XDOTEXT
        _state.value = _state.value.copy(
            targetHostAddress = hostAddress,
            activeBackend = backend,
            isInitialized = true,
            lastDispatchedCommand = "Connected to $hostAddress using ${backend.displayName}"
        )
    }

    /**
     * Dispatches relative mouse movement to the host cursor.
     */
    fun sendMouseMove(dx: Float, dy: Float) {
        scope.launch(Dispatchers.Default) {
            val intX = dx.toInt()
            val intY = dy.toInt()
            if (intX == 0 && intY == 0) return@launch

            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> "ydotool mousemove -x $intX -y $intY"
                HidBackendType.X11_XDOTEXT -> "xdotool mousemove_relative -- $intX $intY"
                HidBackendType.SYSFS_UHID -> "uinput-inject --rel-x $intX --rel-y $intY"
            }
            _state.value = _state.value.copy(lastDispatchedCommand = cmd)
        }
    }

    /**
     * Dispatches mouse button click (down + up).
     */
    fun sendMouseButton(button: VirtualMouseButton) {
        scope.launch(Dispatchers.Default) {
            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> {
                    // ydotool click 0xC0: left (0xC0), right (0xC1), middle (0xC2)
                    val code = when (button) {
                        VirtualMouseButton.LEFT -> "0xC0"
                        VirtualMouseButton.RIGHT -> "0xC1"
                        VirtualMouseButton.MIDDLE -> "0xC2"
                    }
                    "ydotool click $code"
                }
                HidBackendType.X11_XDOTEXT -> "xdotool click ${button.id}"
                HidBackendType.SYSFS_UHID -> "uinput-inject --click ${button.id}"
            }
            _state.value = _state.value.copy(lastDispatchedCommand = cmd)
        }
    }

    /**
     * Dispatches vertical mouse scrollwheel delta.
     */
    fun sendScroll(deltaY: Float) {
        scope.launch(Dispatchers.Default) {
            val intDelta = deltaY.toInt()
            if (intDelta == 0) return@launch

            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> "ydotool mousemove -w $intDelta"
                HidBackendType.X11_XDOTEXT -> {
                    val button = if (intDelta > 0) 5 else 4
                    "xdotool click $button"
                }
                HidBackendType.SYSFS_UHID -> "uinput-inject --wheel $intDelta"
            }
            _state.value = _state.value.copy(lastDispatchedCommand = cmd)
        }
    }

    /**
     * Types an arbitrary text string on the remote desktop.
     */
    fun sendTextInput(text: String) {
        scope.launch(Dispatchers.Default) {
            val escaped = text.replace("'", "'\\''")
            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> "ydotool type '$escaped'"
                HidBackendType.X11_XDOTEXT -> "xdotool type --delay 12 '$escaped'"
                HidBackendType.SYSFS_UHID -> "uinput-inject --type '$escaped'"
            }
            _state.value = _state.value.copy(lastDispatchedCommand = cmd)
        }
    }

    /**
     * Dispatches special keyboard keys (e.g. Esc, Tab, Enter, Super/Meta).
     */
    fun sendSpecialKey(key: VirtualSpecialKey) {
        scope.launch(Dispatchers.Default) {
            val cmd = when (_state.value.activeBackend) {
                HidBackendType.WAYLAND_YDOTOOL -> "ydotool key ${key.waylandKey}:1 ${key.waylandKey}:0"
                HidBackendType.X11_XDOTEXT -> "xdotool key ${key.x11Key}"
                HidBackendType.SYSFS_UHID -> "uinput-inject --key ${key.waylandKey}"
            }
            _state.value = _state.value.copy(lastDispatchedCommand = cmd)
        }
    }
}
