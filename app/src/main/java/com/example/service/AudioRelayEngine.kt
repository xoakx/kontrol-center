package com.example.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AudioRelayMode {
    HOST_TO_PHONE, // Listen to Host audio on Phone via AudioTrack
    PHONE_TO_HOST  // Send Phone Mic audio to Host via AudioRecord
}

data class AudioRelayState(
    val isStreaming: Boolean = false,
    val mode: AudioRelayMode = AudioRelayMode.HOST_TO_PHONE,
    val volumeGain: Float = 0.85f,
    val isMuted: Boolean = false,
    val bufferLatencyMs: Int = 20, // 10ms ultra, 20ms optimal, 50ms stable
    val sampleRateHz: Int = 48000,
    val channels: String = "Stereo (2.0)",
    val bitrateKbps: Int = 1536, // 48kHz * 16-bit * 2 ch = 1536 kbps uncompressed PCM
    val packetsTransferred: Long = 0L,
    val underruns: Int = 0,
    val targetHost: String = "192.168.1.100",
    val targetPort: Int = 4713,
    val visualizerAmplitudes: List<Float> = List(16) { 0.05f },
    val statusMessage: String = "Ready"
)

class AudioRelayEngine(private val scope: CoroutineScope) {

    val audioStreamService = AudioStreamService(scope)

    private val _state = MutableStateFlow(AudioRelayState())
    val state = _state.asStateFlow()

    private var syncJob: Job? = null

    init {
        // Observe real AudioTrack / AudioRecord stats from the streaming service
        scope.launch {
            audioStreamService.stats.collect { stats ->
                if (_state.value.isStreaming) {
                    _state.value = _state.value.copy(
                        packetsTransferred = stats.bytesProcessed / 1024,
                        underruns = stats.underrunCount,
                        visualizerAmplitudes = stats.visualizerBands,
                        statusMessage = stats.statusMessage
                    )
                }
            }
        }
    }

    fun toggleStreaming(targetHost: String = _state.value.targetHost, targetPort: Int = _state.value.targetPort) {
        if (_state.value.isStreaming) {
            stopStreaming()
        } else {
            startStreaming(targetHost, targetPort)
        }
    }

    fun setTargetHost(host: String, port: Int = 4713) {
        _state.value = _state.value.copy(targetHost = host, targetPort = port)
    }

    fun setMode(mode: AudioRelayMode) {
        val wasStreaming = _state.value.isStreaming
        if (wasStreaming) {
            stopStreaming()
        }
        _state.value = _state.value.copy(
            mode = mode,
            statusMessage = if (mode == AudioRelayMode.HOST_TO_PHONE) {
                "Ready: AudioTrack Low-Latency (Host -> Phone)"
            } else {
                "Ready: AudioRecord Mic Relay (Phone -> Host)"
            }
        )
        if (wasStreaming) {
            startStreaming(_state.value.targetHost, _state.value.targetPort)
        }
    }

    fun setVolumeGain(gain: Float) {
        val clamped = gain.coerceIn(0f, 1.5f)
        _state.value = _state.value.copy(volumeGain = clamped)
        audioStreamService.setVolumeGain(clamped)
    }

    fun toggleMute() {
        val muted = audioStreamService.toggleMute()
        _state.value = _state.value.copy(isMuted = muted)
    }

    fun setLatency(latencyMs: Int) {
        val wasStreaming = _state.value.isStreaming
        _state.value = _state.value.copy(bufferLatencyMs = latencyMs)
        if (wasStreaming && _state.value.mode == AudioRelayMode.HOST_TO_PHONE) {
            audioStreamService.stopHostAudioPlayback()
            audioStreamService.startHostAudioPlayback(
                hostIp = _state.value.targetHost,
                hostPort = _state.value.targetPort,
                latencyMs = latencyMs
            )
        }
    }

    private fun startStreaming(host: String = _state.value.targetHost, port: Int = _state.value.targetPort) {
        val currentMode = _state.value.mode

        _state.value = _state.value.copy(
            isStreaming = true,
            targetHost = host,
            targetPort = port,
            statusMessage = if (currentMode == AudioRelayMode.HOST_TO_PHONE) {
                "Starting AudioTrack Low-Latency Engine (48kHz Stereo)..."
            } else {
                "Starting AudioRecord Phone Mic Capture..."
            }
        )

        if (currentMode == AudioRelayMode.HOST_TO_PHONE) {
            audioStreamService.startHostAudioPlayback(
                hostIp = host,
                hostPort = port,
                latencyMs = _state.value.bufferLatencyMs
            )
        } else {
            audioStreamService.startMicrophoneRelayToHost(
                hostIp = host,
                hostPort = port
            )
        }
    }

    private fun stopStreaming() {
        audioStreamService.stopHostAudioPlayback()
        audioStreamService.stopMicrophoneRelay()

        _state.value = _state.value.copy(
            isStreaming = false,
            statusMessage = "Audio Relay Disconnected",
            visualizerAmplitudes = List(16) { 0.05f }
        )
    }
}
