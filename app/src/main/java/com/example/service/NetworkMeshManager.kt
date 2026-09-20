package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random

/**
 * Connection status states conforming to PROJECT.md interface contract.
 */
enum class ConnectionStatus {
    CONNECTED_TAILSCALE,
    CONNECTED_LAN,
    OFFLINE
}

/**
 * Represents the current active mesh endpoint state and measured latency.
 */
data class EndpointState(
    val status: ConnectionStatus,
    val activeBaseUrl: String,
    val activeIp: String,
    val latencyMs: Long,
    val lastChecked: Long
)

/**
 * Public interface contract for dual-path mesh routing.
 */
interface NetworkMeshManager {
    val endpointState: StateFlow<EndpointState>
    fun triggerImmediateCheck()
    suspend fun resolveActiveBaseUrl(): String
}

/**
 * Result of a single endpoint TCP probe.
 */
data class ProbeResult(
    val isReachable: Boolean,
    val latencyMs: Long,
    val error: Throwable? = null
)

/**
 * Socket prober interface for testability and non-blocking I/O.
 */
fun interface SocketProber {
    suspend fun probe(ip: String, port: Int, timeoutMs: Int): ProbeResult
}

/**
 * Default prober using TCP socket connect timing.
 */
class DefaultSocketProber(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SocketProber {
    override suspend fun probe(ip: String, port: Int, timeoutMs: Int): ProbeResult =
        withContext(ioDispatcher) {
            val startTime = System.nanoTime()
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = timeoutMs
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                }
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                ProbeResult(isReachable = true, latencyMs = elapsedMs)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                ProbeResult(isReachable = false, latencyMs = elapsedMs, error = e)
            }
        }
}

/**
 * Production implementation of NetworkMeshManager.
 * Manages dual-path failover, latency probing, Android network callbacks,
 * and exponential backoff reconnection.
 */
class NetworkMeshManagerImpl(
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val socketProber: SocketProber = DefaultSocketProber(ioDispatcher),
    val tailscaleIp: String = DEFAULT_TAILSCALE_IP,
    val lanIp: String = DEFAULT_LAN_IP,
    val port: Int = DEFAULT_PORT,
    private val probeTimeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS,
    private val connectedProbeIntervalMs: Long = DEFAULT_CONNECTED_PROBE_INTERVAL_MS,
    private val baseBackoffMs: Long = DEFAULT_BASE_BACKOFF_MS,
    private val backoffMultiplier: Double = DEFAULT_BACKOFF_MULTIPLIER,
    private val maxBackoffMs: Long = DEFAULT_MAX_BACKOFF_MS,
    private val jitterFactor: Double = DEFAULT_JITTER_FACTOR,
    private val randomProvider: () -> Double = { Random.nextDouble() }
) : NetworkMeshManager {

    companion object {
        const val TAG = "NetworkMeshManager"
        const val DEFAULT_TAILSCALE_IP = "100.111.123.93"
        const val DEFAULT_LAN_IP = "192.168.1.161"
        const val DEFAULT_PORT = 8899
        const val DEFAULT_PROBE_TIMEOUT_MS = 1500
        const val DEFAULT_CONNECTED_PROBE_INTERVAL_MS = 15000L
        const val DEFAULT_BASE_BACKOFF_MS = 1000L
        const val DEFAULT_BACKOFF_MULTIPLIER = 1.5
        const val DEFAULT_MAX_BACKOFF_MS = 30000L
        const val DEFAULT_JITTER_FACTOR = 0.15

        fun create(
            context: Context,
            scope: CoroutineScope,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO
        ): NetworkMeshManager {
            val manager = NetworkMeshManagerImpl(
                scope = scope,
                ioDispatcher = ioDispatcher
            )
            manager.registerNetworkCallback(context)
            manager.startMonitoring()
            return manager
        }
    }

    private val _endpointState = MutableStateFlow(
        EndpointState(
            status = ConnectionStatus.OFFLINE,
            activeBaseUrl = "",
            activeIp = "",
            latencyMs = -1L,
            lastChecked = 0L
        )
    )
    override val endpointState: StateFlow<EndpointState> = _endpointState.asStateFlow()

    private val probeMutex = Mutex()
    private val immediateCheckChannel = Channel<Unit>(Channel.CONFLATED)
    private var monitorJob: Job? = null

    private var registeredContext: Context? = null
    private var connectivityManager: ConnectivityManager? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.i(TAG, "Network became available ($network) - triggering instant mesh re-probe")
            triggerImmediateCheck()
        }

        override fun onLost(network: Network) {
            Log.i(TAG, "Network lost ($network) - triggering instant mesh re-probe")
            triggerImmediateCheck()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val hasVpn = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            val hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val hasCell = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            Log.d(TAG, "Network capabilities changed: VPN=$hasVpn, WiFi=$hasWifi, Cell=$hasCell")
            triggerImmediateCheck()
        }
    }

    /**
     * Triggers an immediate probe, bypassing any active backoff or interval sleep.
     */
    override fun triggerImmediateCheck() {
        immediateCheckChannel.trySend(Unit)
    }

    /**
     * Resolves the active base URL. If currently connected, returns immediately.
     * If currently OFFLINE, performs a synchronous probe attempt.
     * Throws IOException if both paths are unreachable.
     */
    override suspend fun resolveActiveBaseUrl(): String {
        val current = endpointState.value
        if (current.status != ConnectionStatus.OFFLINE && current.activeBaseUrl.isNotBlank()) {
            return current.activeBaseUrl
        }
        val newState = probeEndpoints()
        if (newState.status != ConnectionStatus.OFFLINE && newState.activeBaseUrl.isNotBlank()) {
            return newState.activeBaseUrl
        }
        throw IOException("Zero-Trust Mesh ($tailscaleIp:$port) and LAN fallback ($lanIp:$port) are both OFFLINE")
    }

    /**
     * Executes non-blocking TCP socket probes against Tailscale and LAN endpoints.
     * Zero-Trust Tailscale takes strict priority.
     */
    suspend fun probeEndpoints(): EndpointState = probeMutex.withLock {
        val now = System.currentTimeMillis()

        // 1. Probe Primary Zero-Trust Mesh (Tailscale)
        val tsResult = socketProber.probe(tailscaleIp, port, probeTimeoutMs)
        if (tsResult.isReachable) {
            val newState = EndpointState(
                status = ConnectionStatus.CONNECTED_TAILSCALE,
                activeBaseUrl = "http://$tailscaleIp:$port",
                activeIp = tailscaleIp,
                latencyMs = tsResult.latencyMs,
                lastChecked = now
            )
            _endpointState.value = newState
            return newState
        }

        // 2. Probe Fallback Local LAN Subnet
        val lanResult = socketProber.probe(lanIp, port, probeTimeoutMs)
        if (lanResult.isReachable) {
            val newState = EndpointState(
                status = ConnectionStatus.CONNECTED_LAN,
                activeBaseUrl = "http://$lanIp:$port",
                activeIp = lanIp,
                latencyMs = lanResult.latencyMs,
                lastChecked = now
            )
            _endpointState.value = newState
            return newState
        }

        // 3. Both failed -> OFFLINE
        val newState = EndpointState(
            status = ConnectionStatus.OFFLINE,
            activeBaseUrl = "",
            activeIp = "",
            latencyMs = -1L,
            lastChecked = now
        )
        _endpointState.value = newState
        return newState
    }

    /**
     * Calculates exponential backoff with +/- 15% random jitter.
     */
    fun computeBackoffDelay(attempt: Int): Long {
        val exponential = (baseBackoffMs * Math.pow(backoffMultiplier, attempt.toDouble())).toLong()
        val capped = minOf(exponential, maxBackoffMs)
        val minJitter = 1.0 - jitterFactor
        val maxJitter = 1.0 + jitterFactor
        val rnd = randomProvider()
        val jitteredMultiplier = minJitter + (rnd * (maxJitter - minJitter))
        return (capped * jitteredMultiplier).toLong().coerceAtLeast(100L)
    }

    /**
     * Starts the background monitoring loop.
     */
    fun startMonitoring() {
        if (monitorJob != null) return
        monitorJob = scope.launch(ioDispatcher) {
            var backoffAttempt = 0
            while (isActive) {
                val state = probeEndpoints()
                val nextDelayMs = if (state.status == ConnectionStatus.OFFLINE) {
                    val delay = computeBackoffDelay(backoffAttempt)
                    backoffAttempt++
                    delay
                } else {
                    backoffAttempt = 0
                    connectedProbeIntervalMs
                }

                // Sleep until next probe, or wake up instantly on triggerImmediateCheck()
                withTimeoutOrNull(nextDelayMs) {
                    immediateCheckChannel.receive()
                    backoffAttempt = 0
                }
            }
        }
    }

    /**
     * Registers Android ConnectivityManager callback for network changes.
     */
    fun registerNetworkCallback(context: Context) {
        if (registeredContext != null) return
        registeredContext = context.applicationContext
        connectivityManager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback)
            Log.i(TAG, "Registered ConnectivityManager.NetworkCallback successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register NetworkCallback: ${e.message}")
        }
    }

    /**
     * Unregisters the network callback.
     */
    fun unregisterNetworkCallback() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering NetworkCallback: ${e.message}")
        } finally {
            registeredContext = null
            connectivityManager = null
        }
    }

    /**
     * Cleans up background jobs and unregisters callbacks.
     */
    fun close() {
        unregisterNetworkCallback()
        monitorJob?.cancel()
    }
}
