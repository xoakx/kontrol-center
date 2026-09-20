package com.example.e2e.tier1_features

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
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tier 1 Tests for Feature 1: Zero-Trust Mesh & Dual Endpoint Routing.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F01_01: Successful primary Tailscale mesh resolution
 * - T1_F01_02: Automatic LAN fallback on Tailscale failure
 * - T1_F01_03: Dual-endpoint unreachable disconnect (OFFLINE)
 * - T1_F01_04: Strict connection mode policy enforcement and URL resolution
 * - T1_F01_05: Network interface handover and exponential backoff timing
 */
@OptIn(ExperimentalCoroutinesApi::class)
class F01_ZeroTrustMeshTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
    }

    @Test
    fun T1_F01_01_successful_tailscale_mesh_resolution() = testScope.runTest {
        // Precondition: Tailscale responsive (latency 12ms), LAN also responsive
        val fakeProber = SocketProber { ip, _, _ ->
            when (ip) {
                "100.111.123.93" -> ProbeResult(isReachable = true, latencyMs = 12L)
                "192.168.1.161" -> ProbeResult(isReachable = true, latencyMs = 4L)
                else -> ProbeResult(isReachable = false, latencyMs = -1L)
            }
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = fakeProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 8899
        )

        // Action
        val state = meshManager.probeEndpoints()

        // Assertions: Zero-Trust Tailscale takes strict priority
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state.status)
        assertEquals("http://100.111.123.93:8899", state.activeBaseUrl)
        assertEquals("100.111.123.93", state.activeIp)
        assertEquals(12L, state.latencyMs)
        assertTrue(state.lastChecked > 0)
        assertEquals(state, meshManager.endpointState.value)
    }

    @Test
    fun T1_F01_02_automatic_lan_fallback_on_mesh_failure() = testScope.runTest {
        // Precondition: Tailscale unreachable/timeout, LAN reachable (latency 3ms)
        val fakeProber = SocketProber { ip, _, _ ->
            when (ip) {
                "100.111.123.93" -> ProbeResult(isReachable = false, latencyMs = 1500L, error = IOException("Connection timed out"))
                "192.168.1.161" -> ProbeResult(isReachable = true, latencyMs = 3L)
                else -> ProbeResult(isReachable = false, latencyMs = -1L)
            }
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = fakeProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 8899
        )

        // Action
        val state = meshManager.probeEndpoints()

        // Assertions: Seamless fallback to LAN
        assertEquals(ConnectionStatus.CONNECTED_LAN, state.status)
        assertEquals("http://192.168.1.161:8899", state.activeBaseUrl)
        assertEquals("192.168.1.161", state.activeIp)
        assertEquals(3L, state.latencyMs)
        assertTrue(state.lastChecked > 0)
        assertEquals(state, meshManager.endpointState.value)
    }

    @Test
    fun T1_F01_03_dual_endpoint_unreachable_disconnect() = testScope.runTest {
        // Precondition: Both Tailscale and LAN fail
        val fakeProber = SocketProber { _, _, _ ->
            ProbeResult(isReachable = false, latencyMs = -1L, error = IOException("Network unreachable"))
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = fakeProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 8899
        )

        // Action
        val state = meshManager.probeEndpoints()

        // Assertions: OFFLINE state, empty URLs, -1 latency
        assertEquals(ConnectionStatus.OFFLINE, state.status)
        assertEquals("", state.activeBaseUrl)
        assertEquals("", state.activeIp)
        assertEquals(-1L, state.latencyMs)
        assertTrue(state.lastChecked > 0)
    }

    @Test
    fun T1_F01_04_strict_connection_mode_policy_enforcement() = testScope.runTest {
        // Test resolveActiveBaseUrl behavior
        val reachableProber = SocketProber { ip, _, _ ->
            if (ip == "100.111.123.93") ProbeResult(true, 14L) else ProbeResult(false, -1L)
        }

        val meshManagerConnected = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = reachableProber
        )

        val resolvedUrl = meshManagerConnected.resolveActiveBaseUrl()
        assertEquals("http://100.111.123.93:8899", resolvedUrl)

        // Test failure throws IOException when OFFLINE
        val failingProber = SocketProber { _, _, _ ->
            ProbeResult(false, -1L, IOException("Socket closed"))
        }

        val meshManagerFailing = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = failingProber
        )

        try {
            meshManagerFailing.resolveActiveBaseUrl()
            fail("Expected IOException when resolving URL while OFFLINE")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("OFFLINE") == true)
        }
    }

    @Test
    fun T1_F01_05_network_interface_handover_reprobe() = testScope.runTest {
        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            baseBackoffMs = 1000L,
            backoffMultiplier = 1.5,
            maxBackoffMs = 30000L,
            jitterFactor = 0.0 // Zero jitter for deterministic math
        )

        // Verify exponential backoff math progression
        val delay0 = meshManager.computeBackoffDelay(0) // 1000 * 1.5^0 = 1000
        val delay1 = meshManager.computeBackoffDelay(1) // 1000 * 1.5^1 = 1500
        val delay2 = meshManager.computeBackoffDelay(2) // 1000 * 1.5^2 = 2250
        val delay3 = meshManager.computeBackoffDelay(3) // 1000 * 1.5^3 = 3375
        val delayMax = meshManager.computeBackoffDelay(20) // Exceeds cap -> 30000

        assertEquals(1000L, delay0)
        assertEquals(1500L, delay1)
        assertEquals(2250L, delay2)
        assertEquals(3375L, delay3)
        assertEquals(30000L, delayMax)

        // Immediate check trigger does not throw
        meshManager.triggerImmediateCheck()
    }
}
