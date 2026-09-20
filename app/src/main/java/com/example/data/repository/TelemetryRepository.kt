package com.example.data.repository

import android.util.Log
import com.example.data.api.ArcadeApiService
import com.example.data.api.ArcadeWebSocketClient
import com.example.data.api.CpuTelemetryDto
import com.example.data.api.DispatchRequest
import com.example.data.api.DispatchResponse
import com.example.data.api.GpuTelemetryDto
import com.example.data.api.MemoryTelemetryDto
import com.example.data.api.NpuTelemetryDto
import com.example.data.api.StorageTelemetryDto
import com.example.data.api.TelemetryResponse
import com.example.data.entity.HostEntity
import com.example.service.SshConnectionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * TelemetryRepository aggregates streaming and REST telemetry from host silicon:
 * - Dual NVIDIA GeForce RTX 5060 Ti 16GB GPUs (temperatures, VRAM usage, utilization %, wattage, fan speeds, clocks)
 * - Intel Core Ultra 7 265K 20-core CPU (8 P-cores + 12 E-cores, load averages, temperature, scaling governor)
 * - OpenVINO Intel AI Boost NPU accelerator (:8002 health and device path)
 * - RAM and NVMe storage
 * - Tunables: CPU scaling governor ("performance" vs "powersave") and 1-tap audio pipeline re-anchoring
 */
class TelemetryRepository(
    private val apiService: ArcadeApiService,
    val webSocketClient: ArcadeWebSocketClient? = null,
    private val sshConnectionManager: SshConnectionManager? = SshConnectionManager,
    private val hostProvider: (() -> HostEntity?)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    coroutineScope: CoroutineScope? = null
) {
    companion object {
        private const val TAG = "TelemetryRepository"

        const val THERMAL_SPIKE_THRESHOLD_C = 85.0
        const val THERMAL_CRITICAL_THRESHOLD_C = 95.0

        fun isThermalSpike(telemetry: TelemetryResponse?): Boolean {
            if (telemetry == null) return false
            val peakGpu = telemetry.gpus.maxOfOrNull { it.tempC } ?: 0
            val cpuTemp = telemetry.cpu.tempC
            return peakGpu >= THERMAL_SPIKE_THRESHOLD_C || cpuTemp >= THERMAL_SPIKE_THRESHOLD_C
        }

        fun isCriticalThermal(telemetry: TelemetryResponse?): Boolean {
            if (telemetry == null) return false
            val peakGpu = telemetry.gpus.maxOfOrNull { it.tempC } ?: 0
            val cpuTemp = telemetry.cpu.tempC
            return peakGpu >= THERMAL_CRITICAL_THRESHOLD_C || cpuTemp >= THERMAL_CRITICAL_THRESHOLD_C
        }
    }

    private val _telemetryState = MutableStateFlow<TelemetryResponse?>(null)
    val telemetry: StateFlow<TelemetryResponse?> = _telemetryState.asStateFlow()

    val gpus: Flow<List<GpuTelemetryDto>> = _telemetryState.map { it?.gpus ?: emptyList() }
    val cpu: Flow<CpuTelemetryDto?> = _telemetryState.map { it?.cpu }
    val npu: Flow<NpuTelemetryDto?> = _telemetryState.map { it?.npu }
    val memory: Flow<MemoryTelemetryDto?> = _telemetryState.map { it?.memory }
    val storage: Flow<StorageTelemetryDto?> = _telemetryState.map { it?.storage }

    constructor(apiService: ArcadeApiService) : this(
        apiService = apiService,
        webSocketClient = null,
        sshConnectionManager = SshConnectionManager,
        hostProvider = null,
        ioDispatcher = Dispatchers.IO,
        coroutineScope = null
    )

    constructor(apiService: ArcadeApiService, webSocketClient: ArcadeWebSocketClient) : this(
        apiService = apiService,
        webSocketClient = webSocketClient,
        sshConnectionManager = SshConnectionManager,
        hostProvider = null,
        ioDispatcher = Dispatchers.IO,
        coroutineScope = null
    )

    init {
        if (webSocketClient != null && coroutineScope != null) {
            coroutineScope.launch {
                webSocketClient.telemetryEvents.collect { incoming ->
                    if (incoming != null) {
                        _telemetryState.value = incoming
                    }
                }
            }
        }
    }

    /**
     * Polls the latest hardware telemetry snapshot from Arcade HTTP endpoint.
     */
    suspend fun getTelemetry(): Result<TelemetryResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getTelemetry()
            _telemetryState.value = response
            Result.success(response)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "getTelemetry failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sets CPU governor profile (performance lock vs powersave).
     * Dispatches action to Arcade API, with SSH fallback.
     */
    suspend fun setCpuGovernor(governor: String, host: HostEntity? = null): Result<DispatchResponse> = withContext(ioDispatcher) {
        val action = if (governor.equals("powersave", ignoreCase = true)) "powersave_mode" else "perf_lock"
        try {
            val response = apiService.dispatchAction(DispatchRequest(action = action))
            updateGovernorState(governor)
            Result.success(response)
        } catch (httpEx: Exception) {
            if (httpEx is kotlinx.coroutines.CancellationException) throw httpEx
            Log.w(TAG, "setCpuGovernor over HTTP failed: ${httpEx.message}. Attempting SSH fallback.")
            val targetHost = host ?: hostProvider?.invoke()
            if (targetHost != null && sshConnectionManager != null) {
                val cmd = if (governor.equals("powersave", ignoreCase = true)) {
                    "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo powersave > \"\$g\" 2>/dev/null || true; done; sudo tuned-adm profile powersave 2>/dev/null || true"
                } else {
                    "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > \"\$g\" 2>/dev/null || true; done; sudo tuned-adm profile throughput-performance 2>/dev/null || true"
                }
                val sshResult = sshConnectionManager.executeCommand(targetHost, cmd)
                if (sshResult.isSuccess) {
                    updateGovernorState(governor)
                    return@withContext Result.success(
                        DispatchResponse(
                            success = true,
                            action = action,
                            message = "Governor changed to '$governor' via SSH fallback: ${sshResult.stdout.trim()}"
                        )
                    )
                }
            }
            Result.failure(httpEx)
        }
    }

    /**
     * Triggers 1-tap audio pipeline re-anchoring recovery action.
     * Restarts PipeWire, re-creates virtual null sinks, and bounces audio daemons.
     */
    suspend fun reanchorAudioPipeline(host: HostEntity? = null): Result<DispatchResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.dispatchAction(DispatchRequest(action = "audio_reanchor"))
            Result.success(response)
        } catch (httpEx: Exception) {
            if (httpEx is kotlinx.coroutines.CancellationException) throw httpEx
            Log.w(TAG, "reanchorAudioPipeline over HTTP failed: ${httpEx.message}. Attempting SSH fallback.")
            val targetHost = host ?: hostProvider?.invoke()
            if (targetHost != null && sshConnectionManager != null) {
                val cmd = "systemctl --user restart pipewire pipewire-pulse wireplumber && /home/kms/gemini_master/projects/audio_logger_v2/arbitrator/create_sinks.sh && systemctl --user restart arbitrator.service transcriber.service audio-webui.service"
                val sshResult = sshConnectionManager.executeCommand(targetHost, cmd)
                if (sshResult.isSuccess) {
                    return@withContext Result.success(
                        DispatchResponse(
                            success = true,
                            action = "audio_reanchor",
                            message = "Audio pipeline re-anchored successfully via SSH fallback"
                        )
                    )
                }
            }
            Result.failure(httpEx)
        }
    }

    /**
     * Runs autonomous SRE sweep diagnostic on host.
     */
    suspend fun triggerSreSweep(): Result<DispatchResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.dispatchAction(DispatchRequest(action = "sre_sweep"))
            Result.success(response)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    /**
     * Triggers Git sync and working tree repository audit.
     */
    suspend fun triggerGitSync(): Result<DispatchResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.dispatchAction(DispatchRequest(action = "git_sync"))
            Result.success(response)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    private fun updateGovernorState(newGov: String) {
        _telemetryState.update { cur ->
            cur?.copy(
                cpu = cur.cpu.copy(governor = newGov)
            )
        }
    }
}
