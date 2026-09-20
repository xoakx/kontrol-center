package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.CrowdSecBouncerItem
import com.example.data.api.CrowdSecDecisionItem
import com.example.data.api.FirewallStatusResponse
import com.example.data.api.NetSecOverviewResponse
import com.example.data.api.SuricataAlertItem
import com.example.data.api.TetragonStatusResponse
import com.example.data.entity.HostEntity
import com.example.data.repository.NetSecRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetSecUiState(
    val isLoading: Boolean = false,
    val overview: NetSecOverviewResponse? = null,
    val suricataAlerts: List<SuricataAlertItem> = emptyList(),
    val filteredAlerts: List<SuricataAlertItem> = emptyList(),
    val crowdSecDecisions: List<CrowdSecDecisionItem> = emptyList(),
    val bouncers: List<CrowdSecBouncerItem> = emptyList(),
    val tetragonStatus: TetragonStatusResponse? = null,
    val firewallStatus: FirewallStatusResponse? = null,
    val selectedSeverityFilter: Int? = null, // null = ALL, 1 = CRITICAL, 2 = WARNING, 3 = INFO
    val searchQuery: String = "",
    val selectedAlert: SuricataAlertItem? = null, // Active alert selected for detail modal inspection
    val unbanInProgressIp: String? = null, // IP address currently being unbanned
    val isUnbanningIp: String? = null, // UI compatibility alias for unbanInProgressIp
    val overallHealth: String = "OPTIMAL", // "OPTIMAL", "WARNING", "CRITICAL"
    val errorMessage: String? = null,
    val userNotice: String? = null
)

class NetSecViewModel(
    private val repository: NetSecRepository,
    private val scope: CoroutineScope? = null
) : ViewModel() {

    companion object {
        fun severityLabel(severity: Int): String = when (severity) {
            1 -> "CRITICAL"
            2 -> "WARNING"
            3 -> "INFO"
            else -> "UNKNOWN"
        }

        fun evaluateOverallHealth(
            overview: NetSecOverviewResponse?,
            alerts: List<SuricataAlertItem>
        ): String {
            val banCount = overview?.crowdsecBanCount ?: 0
            val hasCritical = alerts.any { it.alert.severity == 1 }
            val hasWarning = alerts.any { it.alert.severity == 2 }

            return when {
                banCount > 5 || hasCritical -> "CRITICAL"
                banCount > 0 || hasWarning -> "WARNING"
                else -> "OPTIMAL"
            }
        }
    }

    private val currentScope: CoroutineScope
        get() = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(NetSecUiState())
    val uiState: StateFlow<NetSecUiState> = _uiState.asStateFlow()

    init {
        currentScope.launch {
            repository.overview.collect { overview ->
                _uiState.update { current ->
                    current.copy(
                        overview = overview,
                        overallHealth = evaluateOverallHealth(overview, current.suricataAlerts)
                    )
                }
            }
        }

        currentScope.launch {
            repository.suricataAlerts.collect { alerts ->
                _uiState.update { current ->
                    val filtered = applyFilter(alerts, current.selectedSeverityFilter, current.searchQuery)
                    current.copy(
                        suricataAlerts = alerts,
                        filteredAlerts = filtered,
                        overallHealth = evaluateOverallHealth(current.overview, alerts)
                    )
                }
            }
        }

        currentScope.launch {
            repository.crowdSecDecisions.collect { decisions ->
                _uiState.update { it.copy(crowdSecDecisions = decisions) }
            }
        }

        currentScope.launch {
            repository.bouncers.collect { bouncers ->
                _uiState.update { it.copy(bouncers = bouncers) }
            }
        }

        currentScope.launch {
            repository.tetragonStatus.collect { tetragon ->
                _uiState.update { it.copy(tetragonStatus = tetragon) }
            }
        }

        currentScope.launch {
            repository.firewallStatus.collect { fw ->
                _uiState.update { it.copy(firewallStatus = fw) }
            }
        }

        refresh()
    }

    fun refresh(): Job = currentScope.launch {
        refreshInternal()
    }

    fun refreshAll(): Job = refresh()

    private suspend fun refreshInternal() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        val result = repository.refreshAll()
        result.fold(
            onSuccess = { overview ->
                _uiState.update { current ->
                    val alerts = repository.suricataAlerts.value
                    val decisions = repository.crowdSecDecisions.value
                    val bouncers = repository.bouncers.value
                    val tetragon = repository.tetragonStatus.value
                    val firewall = repository.firewallStatus.value
                    val filtered = applyFilter(alerts, current.selectedSeverityFilter, current.searchQuery)
                    current.copy(
                        isLoading = false,
                        overview = overview,
                        suricataAlerts = alerts,
                        filteredAlerts = filtered,
                        crowdSecDecisions = decisions,
                        bouncers = bouncers,
                        tetragonStatus = tetragon,
                        firewallStatus = firewall,
                        overallHealth = evaluateOverallHealth(overview, alerts)
                    )
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "NetSec telemetry refresh failed: ${err.message}"
                    )
                }
            }
        )
    }

    /**
     * Filters alerts by severity: 1 (Critical), 2 (Warning), 3 (Info), or null for all.
     */
    fun setSeverityFilter(severity: Int?) {
        _uiState.update { current ->
            val filtered = applyFilter(current.suricataAlerts, severity, current.searchQuery)
            current.copy(
                selectedSeverityFilter = severity,
                filteredAlerts = filtered
            )
        }
    }

    /**
     * Searches alerts by matching query against IP addresses, signature, or category.
     */
    fun setSearchQuery(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.suricataAlerts, current.selectedSeverityFilter, query)
            current.copy(
                searchQuery = query,
                filteredAlerts = filtered
            )
        }
    }

    /**
     * Selects an alert for raw inspection dialog.
     */
    fun selectAlert(alert: SuricataAlertItem?) {
        _uiState.update { it.copy(selectedAlert = alert) }
    }

    fun dismissAlertDetails() {
        _uiState.update { it.copy(selectedAlert = null) }
    }

    /**
     * Executes 1-tap unban remediation for an IP.
     */
    fun unbanIp(ip: String, host: HostEntity? = null): Job = currentScope.launch {
        val trimmed = ip.trim()
        if (!NetSecRepository.isValidIpOrCidr(trimmed)) {
            _uiState.update {
                it.copy(errorMessage = "Invalid IP format or injection attempt blocked: '$trimmed'")
            }
            return@launch
        }

        _uiState.update {
            it.copy(
                unbanInProgressIp = trimmed,
                isUnbanningIp = trimmed,
                errorMessage = null
            )
        }
        val result = repository.unbanIp(trimmed, host)
        result.fold(
            onSuccess = { resp ->
                if (resp.success) {
                    val notice = if (resp.message != null && resp.message.contains(trimmed)) {
                        resp.message
                    } else if (resp.message != null) {
                        "${resp.message} for $trimmed"
                    } else {
                        "Successfully unbanned $trimmed"
                    }
                    _uiState.update {
                        it.copy(
                            unbanInProgressIp = null,
                            isUnbanningIp = null,
                            crowdSecDecisions = repository.crowdSecDecisions.value,
                            overview = repository.overview.value,
                            overallHealth = evaluateOverallHealth(repository.overview.value, it.suricataAlerts),
                            userNotice = notice
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            unbanInProgressIp = null,
                            isUnbanningIp = null,
                            errorMessage = resp.message ?: "Failed to unban $trimmed"
                        )
                    }
                }
            },
            onFailure = { err ->
                _uiState.update {
                    it.copy(
                        unbanInProgressIp = null,
                        isUnbanningIp = null,
                        errorMessage = "Unban failed for $trimmed: ${err.message}"
                    )
                }
            }
        )
    }

    fun clearNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun applyFilter(
        alerts: List<SuricataAlertItem>,
        severity: Int?,
        query: String
    ): List<SuricataAlertItem> {
        var result = if (severity == null) alerts else alerts.filter { it.alert.severity == severity }
        val q = query.trim()
        if (q.isNotEmpty()) {
            result = result.filter {
                it.srcIp.contains(q, ignoreCase = true) ||
                it.destIp.contains(q, ignoreCase = true) ||
                it.alert.signature.contains(q, ignoreCase = true) ||
                it.alert.category.contains(q, ignoreCase = true)
            }
        }
        return result
    }
}
