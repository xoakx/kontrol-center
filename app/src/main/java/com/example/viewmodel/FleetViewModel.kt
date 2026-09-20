package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.AgentStatusItem
import com.example.data.entity.HostEntity
import com.example.data.repository.FleetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FleetUiState(
    val isLoading: Boolean = false,
    val daemons: List<AgentStatusItem> = emptyList(),
    val activeCount: Int = 0,
    val totalCount: Int = 0,
    val failedCount: Int = 0,
    val overallHealth: String = "OPTIMAL", // "OPTIMAL", "WARNING", "CRITICAL"
    val actionInProgress: String? = null,
    val errorMessage: String? = null,
    val userNotice: String? = null
)

class FleetViewModel(
    private val fleetRepository: FleetRepository,
    private val scope: CoroutineScope? = null
) : ViewModel() {

    private val currentScope: CoroutineScope
        get() = scope ?: viewModelScope

    private val _uiState = MutableStateFlow(FleetUiState())
    val uiState: StateFlow<FleetUiState> = _uiState.asStateFlow()

    init {
        currentScope.launch {
            fleetRepository.supervisedAgents.collect { agents ->
                updateFromAgents(agents)
            }
        }
        refresh()
    }

    fun refresh(): Job = currentScope.launch {
        refreshInternal()
    }

    private suspend fun refreshInternal() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        val result = fleetRepository.getFleetStatus()
        result.fold(
            onSuccess = { response ->
                val filtered = if (response.agents.any { it.id in FleetRepository.SUPERVISED_DAEMON_IDS }) {
                    response.agents.filter { it.id in FleetRepository.SUPERVISED_DAEMON_IDS }
                } else {
                    response.agents
                }
                updateFromAgents(filtered)
                _uiState.value = _uiState.value.copy(isLoading = false)
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load fleet status: ${error.message}"
                )
            }
        )
    }

    fun restartDaemon(service: String, host: HostEntity? = null): Job =
        executeDaemonAction(service, "restart", host)

    fun stopDaemon(service: String, host: HostEntity? = null): Job =
        executeDaemonAction(service, "stop", host)

    fun startDaemon(service: String, host: HostEntity? = null): Job =
        executeDaemonAction(service, "start", host)

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(userNotice = null)
    }

    private fun executeDaemonAction(service: String, action: String, host: HostEntity?): Job =
        currentScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgress = service)
            val result = fleetRepository.controlAgent(service = service, action = action, host = host)
            result.fold(
                onSuccess = { resp ->
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        userNotice = "${resp.service ?: service} ${resp.action ?: action} succeeded."
                    )
                    // Refresh status after mutation directly without re-launching Job and calling join()
                    refreshInternal()
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(
                        actionInProgress = null,
                        errorMessage = "Action $action failed for $service: ${err.message}"
                    )
                }
            )
        }

    private fun updateFromAgents(agents: List<AgentStatusItem>) {
        val total = agents.size
        val active = agents.count { it.status.active }
        val failed = agents.count { it.status.state == "failed" }

        val health = when {
            failed > 0 -> "WARNING"
            active < total && agents.any { !it.status.active && !FleetRepository.isStandbyDaemon(it.id) } -> "WARNING"
            else -> "OPTIMAL"
        }

        _uiState.value = _uiState.value.copy(
            daemons = agents,
            activeCount = active,
            totalCount = total,
            failedCount = failed,
            overallHealth = health
        )
    }
}
