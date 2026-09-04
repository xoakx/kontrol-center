package com.example.service

import androidx.compose.ui.geometry.Offset
import com.example.data.entity.HostEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class DisplayMode {
    MIRROR_HOST,     // View and control host desktop (KRDP / VNC)
    VIRTUAL_MONITOR  // Phone acts as an extended second monitor for the host!
}

data class ScreenDisplayState(
    val isConnected: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.MIRROR_HOST,
    val selectedMonitor: String = "Display 1 (KDE Wayland Primary)",
    val resolution: String = "1920x1080 (60 FPS)",
    val fps: Int = 60,
    val latencyMs: Int = 16,
    val cursorPosition: Offset = Offset(400f, 300f),
    val isLeftMouseDown: Boolean = false,
    val isRightMouseDown: Boolean = false,
    val lastInputFeedback: String = "Input Ready",
    val activeWindowTitle: String = "kwin_wayland [KRDP Port 3389]",
    val rdpPort: Int = 3389,
    val isFullscreen: Boolean = false
)

class ScreenDisplayEngine(
    private val scope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val _state = MutableStateFlow(ScreenDisplayState())
    val state = _state.asStateFlow()

    private var virtualInputService: VirtualInputService? = null
    private var activeHost: HostEntity? = null

    fun bindInputService(service: VirtualInputService) {
        this.virtualInputService = service
    }

    fun attachHost(host: HostEntity) {
        this.activeHost = host
        _state.value = _state.value.copy(
            isConnected = true,
            activeWindowTitle = "${host.name} : kwin_wayland (KRDP ${host.address}:3389)"
        )
    }

    fun setDisplayMode(mode: DisplayMode) {
        _state.value = _state.value.copy(
            displayMode = mode,
            lastInputFeedback = if (mode == DisplayMode.VIRTUAL_MONITOR) {
                "Virtual Monitor Attached: Phone acting as Extended Display"
            } else {
                "Mirroring Host Display via KRDP"
            }
        )
    }

    fun setResolution(res: String) {
        _state.value = _state.value.copy(resolution = res)
    }

    fun setMonitor(monitor: String) {
        _state.value = _state.value.copy(selectedMonitor = monitor)
    }

    fun toggleFullscreen() {
        _state.value = _state.value.copy(isFullscreen = !_state.value.isFullscreen)
    }

    fun sendPointerMove(dx: Float, dy: Float) {
        val current = _state.value.cursorPosition
        val newX = (current.x + dx).coerceIn(20f, 1900f)
        val newY = (current.y + dy).coerceIn(20f, 1060f)
        _state.value = _state.value.copy(
            cursorPosition = Offset(newX, newY),
            lastInputFeedback = "Pointer moved: (${newX.toInt()}, ${newY.toInt()})"
        )
        // Dispatch real HID mouse move via bound service
        virtualInputService?.sendMouseMove(dx, dy)
    }

    fun sendClick(isRightClick: Boolean = false) {
        val button = if (isRightClick) "Right-Click" else "Left-Click"
        _state.value = _state.value.copy(
            lastInputFeedback = "$button dispatched to host"
        )
        val mouseBtn = if (isRightClick) VirtualMouseButton.RIGHT else VirtualMouseButton.LEFT
        virtualInputService?.sendMouseButton(mouseBtn)
    }

    fun sendScroll(scrollDeltaY: Float) {
        val dir = if (scrollDeltaY > 0) "Down" else "Up"
        _state.value = _state.value.copy(
            lastInputFeedback = "Scroll $dir sent to host"
        )
        val key = if (scrollDeltaY > 0) "Down" else "Up"
        virtualInputService?.sendKeyPress(key)
    }

    fun sendKeyPress(key: String) {
        _state.value = _state.value.copy(
            lastInputFeedback = "Key sent: $key"
        )
        virtualInputService?.sendKeyPress(key)
    }

    fun sendMediaAction(action: String) {
        _state.value = _state.value.copy(
            lastInputFeedback = "Media action: $action"
        )
        val host = activeHost ?: return
        scope?.launch(ioDispatcher) {
            val cmd = when (action.lowercase()) {
                "play", "pause", "play/pause" -> "playerctl play-pause 2>/dev/null || xdotool key XF86AudioPlay"
                "next" -> "playerctl next 2>/dev/null || xdotool key XF86AudioNext"
                "previous", "prev" -> "playerctl previous 2>/dev/null || xdotool key XF86AudioPrev"
                "volup", "volume_up" -> "wpctl set-volume @DEFAULT_AUDIO_SINK@ 5%+ 2>/dev/null || pactl set-sink-volume @DEFAULT_SINK@ +5%"
                "voldown", "volume_down" -> "wpctl set-volume @DEFAULT_AUDIO_SINK@ 5%- 2>/dev/null || pactl set-sink-volume @DEFAULT_SINK@ -5%"
                "mute" -> "wpctl set-mute @DEFAULT_AUDIO_SINK@ toggle 2>/dev/null || pactl set-sink-mute @DEFAULT_SINK@ toggle"
                else -> "echo 'media action: $action'"
            }
            try {
                SshConnectionManager.executeCommand(host, cmd, timeoutMs = 2000)
            } catch (_: Exception) {}
        }
    }
}
