package com.example.service

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DisplayMode {
    MIRROR_HOST,     // View and control host desktop (VNC/RDP)
    VIRTUAL_MONITOR  // Phone acts as an extended second monitor for the host!
}

data class ScreenDisplayState(
    val isConnected: Boolean = true,
    val displayMode: DisplayMode = DisplayMode.MIRROR_HOST,
    val selectedMonitor: String = "Display 1 (Primary - 1920x1080)",
    val resolution: String = "1920x1080 (60 FPS)",
    val fps: Int = 60,
    val latencyMs: Int = 16,
    val cursorPosition: Offset = Offset(400f, 300f),
    val isLeftMouseDown: Boolean = false,
    val isRightMouseDown: Boolean = false,
    val lastInputFeedback: String = "Input Ready",
    val activeWindowTitle: String = "andrew@workstation: ~/projects",
    val isFullscreen: Boolean = false
)

class ScreenDisplayEngine {

    private val _state = MutableStateFlow(ScreenDisplayState())
    val state = _state.asStateFlow()

    fun setDisplayMode(mode: DisplayMode) {
        _state.value = _state.value.copy(
            displayMode = mode,
            lastInputFeedback = if (mode == DisplayMode.VIRTUAL_MONITOR) {
                "Virtual Monitor Attached: Phone acting as Extended Display"
            } else {
                "Mirroring Host Display (Display 1)"
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
    }

    fun sendClick(isRightClick: Boolean = false) {
        val button = if (isRightClick) "Right-Click" else "Left-Click"
        _state.value = _state.value.copy(
            lastInputFeedback = "$button sent to host at (${_state.value.cursorPosition.x.toInt()}, ${_state.value.cursorPosition.y.toInt()})"
        )
    }

    fun sendScroll(scrollDeltaY: Float) {
        val dir = if (scrollDeltaY > 0) "Down" else "Up"
        _state.value = _state.value.copy(
            lastInputFeedback = "Scroll $dir sent to host"
        )
    }

    fun sendKeyPress(key: String) {
        _state.value = _state.value.copy(
            lastInputFeedback = "Key sent: $key"
        )
    }

    fun sendMediaAction(action: String) {
        _state.value = _state.value.copy(
            lastInputFeedback = "Media action: $action"
        )
    }
}
