package com.example.data.repository

import android.util.Log
import com.example.data.api.ArcadeApiService
import com.example.data.api.CrowdSecBouncerItem
import com.example.data.api.CrowdSecDecisionItem
import com.example.data.api.CrowdSecDecisionsResponse
import com.example.data.api.FirewallStatusResponse
import com.example.data.api.NetSecOverviewResponse
import com.example.data.api.SuricataAlertItem
import com.example.data.api.TetragonStatusResponse
import com.example.data.api.UnbanRequest
import com.example.data.api.UnbanResponse
import com.example.data.entity.HostEntity
import com.example.service.SshCommandResult
import com.example.service.SshConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * NetSecRepository coordinates network security and host SIEM telemetry across:
 * - Suricata 8 IDS real-time intrusion alerts
 * - CrowdSec LAPI active ban decisions and bouncer health
 * - Cilium Tetragon eBPF TracingPolicy enforcement and runtime violation telemetry
 * - Nftables firewall packet drop posture
 * - 1-Tap IP unban remediation with client-side IP sanitization, optimistic eviction, and SSH fallback
 */
class NetSecRepository(
    private val apiService: ArcadeApiService,
    private val sshConnectionManager: SshConnectionManager? = SshConnectionManager,
    private val hostProvider: (() -> HostEntity?)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sshCommandExecutor: (suspend (HostEntity, String) -> SshCommandResult)? = null
) {
    companion object {
        private const val TAG = "NetSecRepository"

        /**
         * Strict IPv4 and CIDR subnet regex guard to block injection attacks.
         * Enforces valid octet boundaries (0..255) and CIDR prefixes (0..32).
         */
        val IP_REGEX = Regex(
            """^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(\/([0-9]|[1-2][0-9]|3[0-2]))?$"""
        )

        fun isValidIpOrCidr(ip: String): Boolean = ip.isNotBlank() && ip.matches(IP_REGEX)
    }

    private val _overview = MutableStateFlow<NetSecOverviewResponse?>(null)
    val overview: StateFlow<NetSecOverviewResponse?> = _overview.asStateFlow()

    private val _suricataAlerts = MutableStateFlow<List<SuricataAlertItem>>(emptyList())
    val suricataAlerts: StateFlow<List<SuricataAlertItem>> = _suricataAlerts.asStateFlow()

    private val _crowdSecDecisions = MutableStateFlow<List<CrowdSecDecisionItem>>(emptyList())
    val crowdSecDecisions: StateFlow<List<CrowdSecDecisionItem>> = _crowdSecDecisions.asStateFlow()

    private val _bouncers = MutableStateFlow<List<CrowdSecBouncerItem>>(emptyList())
    val bouncers: StateFlow<List<CrowdSecBouncerItem>> = _bouncers.asStateFlow()

    private val _tetragonStatus = MutableStateFlow<TetragonStatusResponse?>(null)
    val tetragonStatus: StateFlow<TetragonStatusResponse?> = _tetragonStatus.asStateFlow()

    private val _firewallStatus = MutableStateFlow<FirewallStatusResponse?>(null)
    val firewallStatus: StateFlow<FirewallStatusResponse?> = _firewallStatus.asStateFlow()

    constructor(apiService: ArcadeApiService) : this(
        apiService = apiService,
        sshConnectionManager = SshConnectionManager,
        hostProvider = null,
        ioDispatcher = Dispatchers.IO,
        sshCommandExecutor = null
    )

    constructor(
        apiService: ArcadeApiService,
        sshConnectionManager: SshConnectionManager?,
        hostProvider: (() -> HostEntity?)?,
        ioDispatcher: CoroutineDispatcher
    ) : this(
        apiService = apiService,
        sshConnectionManager = sshConnectionManager,
        hostProvider = hostProvider,
        ioDispatcher = ioDispatcher,
        sshCommandExecutor = null
    )

    /**
     * Ingests top-level NetSec overview statistics.
     */
    suspend fun getNetSecOverview(): Result<NetSecOverviewResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getNetSecOverview()
            _overview.value = response
            Result.success(response)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "getNetSecOverview failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Ingests Suricata 8 intrusion detection alerts stream.
     */
    suspend fun getSuricataAlerts(limit: Int = 50): Result<List<SuricataAlertItem>> = withContext(ioDispatcher) {
        try {
            val response = apiService.getSuricataAlerts(limit)
            val list = response.alerts
            _suricataAlerts.value = list
            Result.success(list)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "getSuricataAlerts failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Ingests active CrowdSec ban decisions and bouncers.
     */
    suspend fun getCrowdSecDecisions(): Result<CrowdSecDecisionsResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getCrowdSecDecisions()
            _crowdSecDecisions.value = response.activeDecisions
            _bouncers.value = response.bouncers
            Result.success(response)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "getCrowdSecDecisions failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Ingests Tetragon eBPF status.
     */
    suspend fun getTetragonStatus(): Result<TetragonStatusResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getTetragonStatus()
            _tetragonStatus.value = response
            Result.success(response)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "getTetragonStatus failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Ingests Nftables firewall status.
     */
    suspend fun getFirewallStatus(): Result<FirewallStatusResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getFirewallStatus()
            _firewallStatus.value = response
            Result.success(response)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "getFirewallStatus failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Comprehensive refresh polling all NetSec subsystems concurrently or sequentially.
     */
    suspend fun refreshAll(): Result<NetSecOverviewResponse> = withContext(ioDispatcher) {
        try {
            val overviewResult = getNetSecOverview()
            getSuricataAlerts(50)
            getCrowdSecDecisions()
            try { getTetragonStatus() } catch (_: Exception) {}
            try { getFirewallStatus() } catch (_: Exception) {}
            overviewResult
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "refreshAll failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Executes 1-tap IP unban remediation.
     * Enforces client-side IP validation guard, optimistic UI eviction, and SSH fallback.
     */
    suspend fun unbanIp(ip: String, host: HostEntity? = null): Result<UnbanResponse> = withContext(ioDispatcher) {
        val trimmedIp = ip.trim()
        if (!isValidIpOrCidr(trimmedIp)) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid IPv4 address or CIDR format: '$trimmedIp'")
            )
        }

        try {
            val request = UnbanRequest(ip = trimmedIp)
            val response = apiService.unbanIp(request)
            if (response.success) {
                applyOptimisticUnban(trimmedIp)
            }
            Result.success(response)
        } catch (httpEx: Exception) {
            if (httpEx is CancellationException) throw httpEx
            Log.w(TAG, "unbanIp failed over HTTP for '$trimmedIp': ${httpEx.message}. Attempting SSH fallback.")

            val targetHost = host ?: hostProvider?.invoke()
            if (targetHost != null && (sshCommandExecutor != null || sshConnectionManager != null)) {
                try {
                    val cmd = "sudo cscli decisions delete -i $trimmedIp"
                    val sshResult = sshCommandExecutor?.invoke(targetHost, cmd)
                        ?: sshConnectionManager!!.executeCommand(targetHost, cmd)
                    if (sshResult.isSuccess) {
                        val fallbackResponse = UnbanResponse(
                            success = true,
                            action = "delete",
                            ip = trimmedIp,
                            output = sshResult.stdout.trim(),
                            message = "Decision for $trimmedIp deleted via SSH fallback"
                        )
                        applyOptimisticUnban(trimmedIp)
                        return@withContext Result.success(fallbackResponse)
                    } else {
                        Log.e(TAG, "SSH fallback unban failed: ${sshResult.stderr}")
                        return@withContext Result.failure(
                            Exception("Arcade HTTP and SSH fallback both failed. SSH stderr: ${sshResult.stderr}", httpEx)
                        )
                    }
                } catch (sshEx: Exception) {
                    if (sshEx is CancellationException) throw sshEx
                    Log.e(TAG, "SSH fallback threw exception: ${sshEx.message}", sshEx)
                    return@withContext Result.failure(sshEx)
                }
            }
            Result.failure(httpEx)
        }
    }

    /**
     * Filters alerts currently stored in memory by severity level (1=Critical, 2=Warning, 3=Info).
     */
    fun getAlertsBySeverity(severity: Int): List<SuricataAlertItem> {
        return _suricataAlerts.value.filter { it.alert.severity == severity }
    }

    /**
     * Searches alerts currently stored in memory by matching query against IP addresses, signature, or category.
     */
    fun searchAlerts(query: String): List<SuricataAlertItem> {
        val q = query.trim()
        if (q.isEmpty()) return _suricataAlerts.value
        return _suricataAlerts.value.filter {
            it.srcIp.contains(q, ignoreCase = true) ||
            it.destIp.contains(q, ignoreCase = true) ||
            it.alert.signature.contains(q, ignoreCase = true) ||
            it.alert.category.contains(q, ignoreCase = true)
        }
    }

    /**
     * Optimistically evicts the unbanned IP from the in-memory decision list and decrements count.
     */
    private fun applyOptimisticUnban(ip: String) {
        _crowdSecDecisions.update { currentList ->
            currentList.filterNot { it.value == ip }
        }
        _overview.update { currentOverview ->
            currentOverview?.copy(
                crowdsecBanCount = (currentOverview.crowdsecBanCount - 1).coerceAtLeast(0)
            )
        }
    }
}
