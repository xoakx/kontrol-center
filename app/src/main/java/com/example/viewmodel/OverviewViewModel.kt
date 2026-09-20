package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.FleetStatusResponse
import com.example.data.api.NetSecOverviewResponse
import com.example.data.api.TelemetryResponse
import com.example.data.entity.HostEntity
import com.example.data.repository.FleetRepository
import com.example.data.repository.HostRepository
import com.example.data.repository.NetSecRepository
import com.example.data.repository.TelemetryRepository
import com.example.service.ConnectionStatus
import com.example.service.EndpointState
import com.example.service.NetworkMeshManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Data model for aggregated executive health overview cards.
 * Conforms to F12_ExecutiveDashTest and S6_ColdBootExecutiveHealthScenario contracts.
 */
data class ExecutiveDashboardSummary(
    val systemHealthCard: String,
    val fleetDaemonsCard: String,
    val securitySiemCard: String,
    val connectedWorkstationsCard: String,
    val overallStatus: String // OPTIMAL, WARNING, CRITICAL, OFFLINE
)

data class OverviewUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val summary: ExecutiveDashboardSummary = ExecutiveDashboardSummary(
        systemHealthCard = "System Health Unavailable",
        fleetDaemonsCard = "Fleet Offline",
        securitySiemCard = "Security Telemetry Offline",
        connectedWorkstationsCard = "Offline",
        overallStatus = "OFFLINE"
    ),
    val telemetry: TelemetryResponse? = null,
    val fleet: FleetStatusResponse? = null,
    val netSec: NetSecOverviewResponse? = null,
    val activeHost: HostEntity? = null,
    val meshState: EndpointState? = null,
    val lastRefreshedEpochMs: Long = 0L,
    val errorMessage: String? = null
)

/**
 * ViewModel orchestrating executive-level aggregate health metrics across
 * Silicon Telemetry (RTX 5060 Ti, Intel Ultra 7, OpenVINO NPU), 12 Supervised Fleet Daemons,
 * NetSec/SIEM threat telemetry (CrowdSec, Suricata 8), and Zero-Trust Mesh network endpoints.
 */
class OverviewViewModel(
    private val telemetryRepository: TelemetryRepository,
    private val fleetRepository: FleetRepository,
    private val netSecRepository: NetSecRepository,
    private val hostRepository: HostRepository,
    private val networkMeshManager: NetworkMeshManager? = null,
    private val scope: CoroutineScope? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val currentScope: CoroutineScope
        get() = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(OverviewUiState(isLoading = true))
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    companion object {
        fun aggregateDashboard(
            telemetry: TelemetryResponse?,
            fleet: FleetStatusResponse?,
            netsec: NetSecOverviewResponse?,
            activeHost: HostEntity?
        ): ExecutiveDashboardSummary {
            val peakGpu = telemetry?.gpus?.maxOfOrNull { it.tempC } ?: 0
            val sysCard = if (telemetry != null) {
                "Optimal • ${peakGpu}°C (CPU: ${telemetry.cpu.tempC.toInt()}°C, RAM: ${telemetry.memory.usedPct.toInt()}%)"
            } else {
                "System Health Unavailable"
            }

            val runningCount = fleet?.agents?.count { it.status.active } ?: 0
            val fleetTotal = fleet?.agents?.size ?: 0
            val fleetCard = if (fleet != null) "$runningCount/$fleetTotal Active" else "Fleet Offline"

            val secCard = if (netsec != null) {
                "${netsec.crowdsecBanCount} Active Bans • ${netsec.suricataAlertCount} Alerts"
            } else {
                "Security Telemetry Offline"
            }

            val hostCard = if (activeHost != null && activeHost.isOnline) {
                val connType = if (activeHost.activeEndpoint.contains("192.168.") || (activeHost.address.startsWith("192.168.") && !activeHost.activeEndpoint.contains("100."))) {
                    "LAN"
                } else {
                    "Tailscale"
                }
                "Connected via $connType (${activeHost.lastLatencyMs}ms)"
            } else {
                "Offline"
            }

            val overall = when {
                activeHost == null || !activeHost.isOnline -> "OFFLINE"
                peakGpu >= 85 || (netsec?.crowdsecBanCount ?: 0) > 5 -> "CRITICAL"
                runningCount < fleetTotal || peakGpu >= 70 || (netsec?.suricataAlertCount ?: 0) > 10 -> "WARNING"
                else -> "OPTIMAL"
            }

            return ExecutiveDashboardSummary(
                systemHealthCard = sysCard,
                fleetDaemonsCard = fleetCard,
                securitySiemCard = secCard,
                connectedWorkstationsCard = hostCard,
                overallStatus = overall
            )
        }
    }

    init {
        currentScope.launch {
            hostRepository.allHosts.collect { hosts ->
                val currentHost = _uiState.value.activeHost
                val targetHost = hosts.find { it.id == currentHost?.id } ?: hosts.firstOrNull()
                _uiState.update { state ->
                    val summary = aggregateDashboard(state.telemetry, state.fleet, state.netSec, targetHost)
                    state.copy(activeHost = targetHost, summary = summary)
                }
            }
        }

        if (networkMeshManager != null) {
            currentScope.launch {
                networkMeshManager.endpointState.collect { meshState ->
                    _uiState.update { state ->
                        val updatedHost = state.activeHost?.copy(
                            lastLatencyMs = meshState.latencyMs,
                            isOnline = meshState.status != ConnectionStatus.OFFLINE,
                            activeEndpoint = meshState.activeBaseUrl
                        )
                        val summary = aggregateDashboard(state.telemetry, state.fleet, state.netSec, updatedHost)
                        state.copy(activeHost = updatedHost, meshState = meshState, summary = summary)
                    }
                }
            }
        }

        refresh()
    }

    fun setActiveHost(host: HostEntity?) {
        _uiState.update { state ->
            val summary = aggregateDashboard(state.telemetry, state.fleet, state.netSec, host)
            state.copy(activeHost = host, summary = summary)
        }
    }

    fun refresh(): Job = currentScope.launch {
        _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
        try {
            val telemetryDeferred = async(ioDispatcher) { telemetryRepository.getTelemetry().getOrNull() }
            val fleetDeferred = async(ioDispatcher) { fleetRepository.getFleetStatus().getOrNull() }
            val netsecDeferred = async(ioDispatcher) { netSecRepository.getNetSecOverview().getOrNull() }

            val telemetry = telemetryDeferred.await()
            val fleet = fleetDeferred.await()
            val netsec = netsecDeferred.await()

            val host = _uiState.value.activeHost
            val summary = aggregateDashboard(telemetry, fleet, netsec, host)

            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    isLoading = false,
                    telemetry = telemetry,
                    fleet = fleet,
                    netSec = netsec,
                    summary = summary,
                    lastRefreshedEpochMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    isLoading = false,
                    errorMessage = e.message
                )
            }
        }
    }
}
