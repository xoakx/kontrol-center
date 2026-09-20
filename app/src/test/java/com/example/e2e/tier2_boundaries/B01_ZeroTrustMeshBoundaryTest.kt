package com.example.e2e.tier2_boundaries

import com.example.service.ConnectionStatus
import com.example.service.NetworkMeshManagerImpl
import com.example.service.ProbeResult
import com.example.service.SocketProber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException

/**
 * Tier 2 Boundary Tests: B01 Zero-Trust Mesh Boundary.
 * Covers 5 boundary conditions:
 * - T2_B01_01: Socket timeouts during endpoint probing (e.g. 1500ms timeout boundary)
 * - T2_B01_02: Rapid connection flapping between Tailscale and LAN over repeated probe cycles
 * - T2_B01_03: Unreachable subnet route failure (NoRouteToHostException)
 * - T2_B01_04: Invalid URL and boundary port formatting (port 0, 65535, invalid IP)
 * - T2_B01_05: Zero-latency and sub-millisecond local loopback edge case
 */
@OptIn(ExperimentalCoroutinesApi::class)
class B01_ZeroTrustMeshBoundaryTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun T2_B01_01_probe_socket_timeout_handling() = testScope.runTest {
        // Socket timeout on both Tailscale and LAN endpoints
        val timeoutProber = SocketProber { ip, _, timeoutMs ->
            ProbeResult(
                isReachable = false,
                latencyMs = timeoutMs.toLong(),
                error = SocketTimeoutException("Connect timed out after ${timeoutMs}ms for $ip")
            )
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = timeoutProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 8899,
            probeTimeoutMs = 1500
        )

        val state = meshManager.probeEndpoints()

        assertEquals(ConnectionStatus.OFFLINE, state.status)
        assertEquals("", state.activeBaseUrl)
        assertEquals("", state.activeIp)
        assertEquals(-1L, state.latencyMs)
        assertTrue(state.lastChecked > 0)
    }

    @Test
    fun T2_B01_02_rapid_connection_flapping() = testScope.runTest {
        // Flapping sequence: Tailscale UP -> BOTH DOWN -> LAN UP -> Tailscale UP -> BOTH DOWN
        val reachabilitySequence = mutableListOf(
            Pair(true, false),  // TS UP, LAN DOWN
            Pair(false, false), // BOTH DOWN
            Pair(false, true),  // TS DOWN, LAN UP
            Pair(true, true),   // TS UP (Priority), LAN UP
            Pair(false, false)  // BOTH DOWN
        )

        var flapIndex = 0
        val flappingProber = SocketProber { ip, _, _ ->
            val (tsReachable, lanReachable) = reachabilitySequence[flapIndex.coerceAtMost(reachabilitySequence.size - 1)]
            val reachable = if (ip == "100.111.123.93") tsReachable else lanReachable
            ProbeResult(isReachable = reachable, latencyMs = if (reachable) 15L else -1L)
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = flappingProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 8899
        )

        // Step 1: TS UP
        flapIndex = 0
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, meshManager.probeEndpoints().status)

        // Step 2: BOTH DOWN
        flapIndex = 1
        assertEquals(ConnectionStatus.OFFLINE, meshManager.probeEndpoints().status)

        // Step 3: LAN UP
        flapIndex = 2
        assertEquals(ConnectionStatus.CONNECTED_LAN, meshManager.probeEndpoints().status)

        // Step 4: TS UP (Priority over LAN)
        flapIndex = 3
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, meshManager.probeEndpoints().status)

        // Step 5: BOTH DOWN
        flapIndex = 4
        assertEquals(ConnectionStatus.OFFLINE, meshManager.probeEndpoints().status)
    }

    @Test
    fun T2_B01_03_unreachable_subnet_route_failure() = testScope.runTest {
        val noRouteProber = SocketProber { ip, _, _ ->
            ProbeResult(
                isReachable = false,
                latencyMs = -1L,
                error = NoRouteToHostException("No route to host: $ip")
            )
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = noRouteProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 8899
        )

        val state = meshManager.probeEndpoints()
        assertEquals(ConnectionStatus.OFFLINE, state.status)

        try {
            meshManager.resolveActiveBaseUrl()
            fail("Expected IOException on resolving active base URL when unreachable")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("OFFLINE") == true)
        }
    }

    @Test
    fun T2_B01_04_invalid_url_and_boundary_port_formatting() = testScope.runTest {
        val portProber = SocketProber { ip, port, _ ->
            // Simulates socket bind on boundary port
            if (port in 1..65535 && ip.isNotBlank()) {
                ProbeResult(isReachable = true, latencyMs = 5L)
            } else {
                ProbeResult(isReachable = false, latencyMs = -1L, error = IllegalArgumentException("Invalid port $port"))
            }
        }

        // Test boundary port 65535
        val meshManagerMaxPort = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = portProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 65535
        )
        val stateMax = meshManagerMaxPort.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, stateMax.status)
        assertEquals("http://100.111.123.93:65535", stateMax.activeBaseUrl)

        // Test boundary port 0 (invalid)
        val meshManagerZeroPort = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = portProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 0
        )
        val stateZero = meshManagerZeroPort.probeEndpoints()
        assertEquals(ConnectionStatus.OFFLINE, stateZero.status)
    }

    @Test
    fun T2_B01_05_zero_latency_and_sub_millisecond_anomaly() = testScope.runTest {
        // Test sub-millisecond local loopback (0ms latency report)
        val zeroLatencyProber = SocketProber { _, _, _ ->
            ProbeResult(isReachable = true, latencyMs = 0L)
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = zeroLatencyProber,
            tailscaleIp = "127.0.0.1",
            lanIp = "127.0.0.1",
            port = 8899
        )

        val state = meshManager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state.status)
        assertEquals(0L, state.latencyMs)
        assertEquals("http://127.0.0.1:8899", state.activeBaseUrl)
    }
}
