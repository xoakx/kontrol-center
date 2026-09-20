package com.example.data.repository

import android.util.Log
import com.example.data.api.AgentControlRequest
import com.example.data.api.AgentControlResponse
import com.example.data.api.AgentControlStatus
import com.example.data.api.AgentServiceStatus
import com.example.data.api.AgentStatusItem
import com.example.data.api.ArcadeApiService
import com.example.data.api.FleetStatusResponse
import com.example.data.entity.HostEntity
import com.example.service.SshConnectionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * FleetRepository manages live discovery, status inspection, and lifecycle mutations
 * (start/stop/restart) for all 12 supervised KMS Master fleet daemons.
 *
 * Primary transport: Arcade SRE Command Center REST API (/api/fleet/status and /api/agents/control).
 * Secondary fallback: Direct authenticated SSH command execution via JSch.
 */
class FleetRepository(
    private val apiService: ArcadeApiService,
    private val sshConnectionManager: SshConnectionManager? = SshConnectionManager,
    private val hostProvider: (() -> HostEntity?)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "FleetRepository"

        val SUPERVISED_DAEMON_IDS = setOf(
            "gemini-scribe",
            "gemini-sre-watchdog",
            "gemini-git-custodian",
            "gemini-npu-embeddings",
            "arbitrator",
            "transcriber",
            "audio-webui",
            "qwen14b-inference",
            "qwen1_5b-reflex",
            "auth-monitor",
            "gemini-openobserve",
            "gemini-vector"
        )

        val STANDBY_DAEMONS = setOf(
            "gemini-git-custodian"
        )

        fun isStandbyDaemon(id: String): Boolean = id in STANDBY_DAEMONS
    }

    private val _agents = MutableStateFlow<List<AgentStatusItem>>(emptyList())
    val agents: StateFlow<List<AgentStatusItem>> = _agents.asStateFlow()

    private val _supervisedAgents = MutableStateFlow<List<AgentStatusItem>>(emptyList())
    val supervisedAgents: StateFlow<List<AgentStatusItem>> = _supervisedAgents.asStateFlow()

    constructor(apiService: ArcadeApiService) : this(
        apiService = apiService,
        sshConnectionManager = SshConnectionManager,
        hostProvider = null,
        ioDispatcher = Dispatchers.IO
    )

    /**
     * Fetches current fleet status from Arcade API.
     * Updates internal StateFlow caches with all agents and filters for the 12 supervised daemons.
     */
    suspend fun getFleetStatus(): Result<FleetStatusResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getFleetStatus()
            val allList = response.agents
            _agents.value = allList

            val filtered = if (allList.any { it.id in SUPERVISED_DAEMON_IDS }) {
                allList.filter { it.id in SUPERVISED_DAEMON_IDS }
            } else {
                allList
            }
            _supervisedAgents.value = filtered
            Result.success(response)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "getFleetStatus failed over HTTP: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Dispatches a lifecycle control mutation (start/stop/restart) for a fleet daemon.
     * Tries Arcade REST API first; on network or HTTP failure, seamlessly falls back to SSH.
     */
    suspend fun controlAgent(
        service: String,
        action: String,
        host: HostEntity? = null
    ): Result<AgentControlResponse> = withContext(ioDispatcher) {
        try {
            val request = AgentControlRequest(service = service, action = action)
            val response = apiService.controlAgent(request)
            updateLocalDaemonState(service, action, response.success)
            Result.success(response)
        } catch (httpEx: Exception) {
            if (httpEx is kotlinx.coroutines.CancellationException) throw httpEx
            Log.w(TAG, "controlAgent failed over HTTP for service '$service': ${httpEx.message}. Attempting SSH fallback.")
            val targetHost = host ?: hostProvider?.invoke()
            if (targetHost != null && sshConnectionManager != null) {
                try {
                    val cmd = "systemctl --user $action $service"
                    val sshResult = sshConnectionManager.executeCommand(targetHost, cmd)
                    if (sshResult.isSuccess) {
                        val isRunning = action != "stop"
                        val fallbackResponse = AgentControlResponse(
                            success = true,
                            service = service,
                            action = action,
                            status = AgentControlStatus(
                                active = isRunning,
                                state = if (isRunning) "running" else "stopped"
                            ),
                            message = "Executed via SSH fallback: ${sshResult.stdout.trim()}"
                        )
                        updateLocalDaemonState(service, action, true)
                        return@withContext Result.success(fallbackResponse)
                    } else {
                        Log.e(TAG, "SSH fallback execution failed: ${sshResult.stderr}")
                        return@withContext Result.failure(
                            Exception("Arcade HTTP and SSH fallback both failed. SSH stderr: ${sshResult.stderr}", httpEx)
                        )
                    }
                } catch (sshEx: Exception) {
                    if (sshEx is kotlinx.coroutines.CancellationException) throw sshEx
                    Log.e(TAG, "SSH fallback threw exception: ${sshEx.message}", sshEx)
                    return@withContext Result.failure(sshEx)
                }
            }
            Result.failure(httpEx)
        }
    }

    suspend fun restartDaemon(service: String, host: HostEntity? = null): Result<AgentControlResponse> =
        controlAgent(service = service, action = "restart", host = host)

    suspend fun stopDaemon(service: String, host: HostEntity? = null): Result<AgentControlResponse> =
        controlAgent(service = service, action = "stop", host = host)

    suspend fun startDaemon(service: String, host: HostEntity? = null): Result<AgentControlResponse> =
        controlAgent(service = service, action = "start", host = host)

    private fun updateLocalDaemonState(service: String, action: String, success: Boolean) {
        if (!success) return
        val isRunning = action != "stop"
        val newState = if (isRunning) "running" else "stopped"

        _agents.update { currentList ->
            currentList.map { daemon ->
                if (daemon.service == service || "${daemon.id}.service" == service) {
                    daemon.copy(
                        status = daemon.status.copy(
                            active = isRunning,
                            state = newState,
                            pid = if (isRunning) daemon.status.pid ?: 10000L else null,
                            memoryMb = if (isRunning) daemon.status.memoryMb ?: 50.0 else 0.0
                        )
                    )
                } else daemon
            }
        }

        _supervisedAgents.update { currentList ->
            currentList.map { daemon ->
                if (daemon.service == service || "${daemon.id}.service" == service) {
                    daemon.copy(
                        status = daemon.status.copy(
                            active = isRunning,
                            state = newState,
                            pid = if (isRunning) daemon.status.pid ?: 10000L else null,
                            memoryMb = if (isRunning) daemon.status.memoryMb ?: 50.0 else 0.0
                        )
                    )
                } else daemon
            }
        }
    }
}
