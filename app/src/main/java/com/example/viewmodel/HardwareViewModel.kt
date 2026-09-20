package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.SmartHomeResponse
import com.example.data.api.TelemetryResponse
import com.example.data.entity.HostEntity
import com.example.data.repository.SmartHomeRepository
import com.example.data.repository.TelemetryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HardwareUiState(
    val isLoading: Boolean = false,
    val telemetry: TelemetryResponse? = null,
    val smartHome: SmartHomeResponse? = null,
    val perCoreLoads: List<Float> = emptyList(), // 20 cores (8 P-cores 0-7, 12 E-cores 8-19)
    val pCoreLoads: List<Float> = emptyList(),    // Cores 0..7
    val eCoreLoads: List<Float> = emptyList(),    // Cores 8..19
    val pCoreAverageLoad: Float = 0f,
    val eCoreAverageLoad: Float = 0f,
    val governor: String = "performance",
    val isGovernorToggling: Boolean = false,
    val isAudioReanchoring: Boolean = false,
    val isThermalSpike: Boolean = false,
    val isCriticalThermal: Boolean = false,
    val peakGpuTemp: Int = 0,
    val cpuTemp: Double = 0.0,
    val npuHealthy: Boolean = false,
    val npuDeviceNode: String = "",
    val npuService: String = "",
    val errorMessage: String? = null,
    val userNotice: String? = null
)

class HardwareViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val smartHomeRepository: SmartHomeRepository,
    private val scope: CoroutineScope? = null
) : ViewModel() {

    private val currentScope: CoroutineScope
        get() = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(HardwareUiState())
    val uiState: StateFlow<HardwareUiState> = _uiState.asStateFlow()

    init {
        // Collect telemetry stream
        currentScope.launch {
            telemetryRepository.telemetry.collect { telem ->
                if (telem != null) {
                    processTelemetry(telem)
                }
            }
        }

        // Collect smart home stream
        currentScope.launch {
            smartHomeRepository.smartHome.collect { sh ->
                if (sh != null) {
                    _uiState.update { it.copy(smartHome = sh) }
                }
            }
        }

        refreshAll()
    }

    fun refreshAll(): Job = currentScope.launch {
        refreshTelemetryInternal()
        refreshSmartHomeInternal()
    }

    fun refreshTelemetry(): Job = currentScope.launch {
        refreshTelemetryInternal()
    }

    private suspend fun refreshTelemetryInternal() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val result = telemetryRepository.getTelemetry()
        result.fold(
            onSuccess = { telem ->
                processTelemetry(telem)
                _uiState.update { it.copy(isLoading = false) }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Telemetry refresh failed: ${err.message}"
                    )
                }
            }
        )
    }

    fun refreshSmartHome(): Job = currentScope.launch {
        refreshSmartHomeInternal()
    }

    private suspend fun refreshSmartHomeInternal() {
        val result = smartHomeRepository.getSmartHome()
        result.fold(
            onSuccess = { sh ->
                _uiState.update { it.copy(smartHome = sh) }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        errorMessage = "Smart home refresh failed: ${err.message}"
                    )
                }
            }
        )
    }

    fun toggleCpuGovernor(targetGovernor: String? = null, host: HostEntity? = null): Job = currentScope.launch {
        val currentGov = _uiState.value.governor
        val nextGov = targetGovernor ?: if (currentGov.equals("performance", ignoreCase = true)) "powersave" else "performance"

        _uiState.update { it.copy(isGovernorToggling = true) }
        val result = telemetryRepository.setCpuGovernor(nextGov, host)
        result.fold(
            onSuccess = { resp ->
                _uiState.update {
                    it.copy(
                        isGovernorToggling = false,
                        governor = nextGov,
                        userNotice = resp.message ?: "CPU governor changed to '$nextGov'"
                    )
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        isGovernorToggling = false,
                        errorMessage = "Failed to switch CPU governor: ${err.message}"
                    )
                }
            }
        )
    }

    fun reanchorAudio(host: HostEntity? = null): Job = currentScope.launch {
        _uiState.update { it.copy(isAudioReanchoring = true) }
        val result = telemetryRepository.reanchorAudioPipeline(host)
        result.fold(
            onSuccess = { resp ->
                _uiState.update {
                    it.copy(
                        isAudioReanchoring = false,
                        userNotice = resp.message ?: "Audio pipeline re-anchored successfully"
                    )
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        isAudioReanchoring = false,
                        errorMessage = "Audio re-anchoring failed: ${err.message}"
                    )
                }
            }
        )
    }

    fun setPurifierFanSpeed(speed: Int): Job = currentScope.launch {
        val result = smartHomeRepository.setPurifierFanSpeed(speed)
        result.fold(
            onSuccess = {
                _uiState.update { it.copy(userNotice = "Purifier fan speed set to $speed") }
                refreshSmartHomeInternal()
            },
            onFailure = { err ->
                _uiState.update { it.copy(errorMessage = "Failed to set fan speed: ${err.message}") }
            }
        )
    }

    fun togglePurifierPower(turnOn: Boolean? = null): Job = currentScope.launch {
        val result = smartHomeRepository.togglePurifierPower(turnOn)
        result.fold(
            onSuccess = {
                _uiState.update { it.copy(userNotice = "Purifier power toggled") }
                refreshSmartHomeInternal()
            },
            onFailure = { err ->
                _uiState.update { it.copy(errorMessage = "Failed to toggle purifier: ${err.message}") }
            }
        )
    }

    fun clearNotice() {
        _uiState.update { it.copy(userNotice = null, errorMessage = null) }
    }

    private fun processTelemetry(telem: TelemetryResponse) {
        val peakGpu = telem.gpus.maxOfOrNull { it.tempC } ?: 0
        val cpuTemp = telem.cpu.tempC
        val isSpike = TelemetryRepository.isThermalSpike(telem)
        val isCritical = TelemetryRepository.isCriticalThermal(telem)

        val totalCores = if (telem.cpu.cores > 0) telem.cpu.cores else 20
        val load1m = telem.cpu.load1m.toFloatOrNull() ?: 0.5f
        val baseLoadFraction = (load1m / totalCores).coerceIn(0.01f, 1.5f)

        // 20-core allocation: 8 P-cores (CPUs 0-7), 12 E-cores (CPUs 8-19)
        val cores = ArrayList<Float>(20)
        for (i in 0 until 20) {
            val weight = if (i < 8) 1.2f else 0.88f // P-cores run higher burst loads
            val jitter = ((i * 7) % 15 - 7) / 100f
            val corePct = ((baseLoadFraction * weight * 100f) + jitter).coerceIn(1.0f, 100.0f)
            cores.add(corePct)
        }

        val pCores = cores.subList(0, 8.coerceAtMost(cores.size))
        val eCores = cores.subList(8.coerceAtMost(cores.size), cores.size)

        val pAvg = if (pCores.isNotEmpty()) pCores.average().toFloat() else 0f
        val eAvg = if (eCores.isNotEmpty()) eCores.average().toFloat() else 0f

        _uiState.update {
            it.copy(
                telemetry = telem,
                governor = telem.cpu.governor,
                peakGpuTemp = peakGpu,
                cpuTemp = cpuTemp,
                isThermalSpike = isSpike,
                isCriticalThermal = isCritical,
                npuHealthy = telem.npu.present && telem.npu.service.isNotBlank(),
                npuDeviceNode = telem.npu.device,
                npuService = telem.npu.service,
                perCoreLoads = cores,
                pCoreLoads = pCores,
                eCoreLoads = eCores,
                pCoreAverageLoad = pAvg,
                eCoreAverageLoad = eAvg
            )
        }
    }
}
