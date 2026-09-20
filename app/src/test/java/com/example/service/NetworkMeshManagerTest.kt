package com.example.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
class NetworkMeshManagerTest {

    class FakeSocketProber : SocketProber {
        val rules = ConcurrentHashMap<String, ProbeResult>()
        val invocationHistory = mutableListOf<String>()

        fun setRule(ip: String, result: ProbeResult) {
            rules[ip] = result
        }

        override suspend fun probe(ip: String, port: Int, timeoutMs: Int): ProbeResult {
            invocationHistory.add("$ip:$port")
            return rules[ip] ?: ProbeResult(isReachable = false, latencyMs = timeoutMs.toLong())
        }
    }

    @Test
    fun testInitialStateIsOffline() {
        val testScope = TestScope()
        val prober = FakeSocketProber()
        val manager = NetworkMeshManagerImpl(
            scope = testScope,
            socketProber = prober
        )

        val state = manager.endpointState.value
        assertEquals(ConnectionStatus.OFFLINE, state.status)
        assertEquals("", state.activeBaseUrl)
        assertEquals("", state.activeIp)
        assertEquals(-1L, state.latencyMs)
    }

    @Test
    fun testTailscaleProbeSuccessSelectsTailscale() = runTest {
        val prober = FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = true, latencyMs = 15L))
            setRule("192.168.1.161", ProbeResult(isReachable = true, latencyMs = 3L))
        }

        val manager = NetworkMeshManagerImpl(
            scope = this,
            socketProber = prober
        )

        val state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state.status)
        assertEquals("http://100.111.123.93:8899", state.activeBaseUrl)
        assertEquals("100.111.123.93", state.activeIp)
        assertEquals(15L, state.latencyMs)
        assertEquals(manager.endpointState.value, state)
    }

    @Test
    fun testTailscaleFailsLanSuccessSelectsLanFallback() = runTest {
        val prober = FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 1500L, error = SocketTimeoutException()))
            setRule("192.168.1.161", ProbeResult(isReachable = true, latencyMs = 4L))
        }

        val manager = NetworkMeshManagerImpl(
            scope = this,
            socketProber = prober
        )

        val state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_LAN, state.status)
        assertEquals("http://192.168.1.161:8899", state.activeBaseUrl)
        assertEquals("192.168.1.161", state.activeIp)
        assertEquals(4L, state.latencyMs)
    }

    @Test
    fun testBothEndpointsFailEntersOfflineState() = runTest {
        val prober = FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 1500L, error = SocketTimeoutException()))
            setRule("192.168.1.161", ProbeResult(isReachable = false, latencyMs = 1500L, error = ConnectException("Connection refused")))
        }

        val manager = NetworkMeshManagerImpl(
            scope = this,
            socketProber = prober
        )

        val state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.OFFLINE, state.status)
        assertEquals("", state.activeBaseUrl)
        assertEquals("", state.activeIp)
        assertEquals(-1L, state.latencyMs)
    }

    @Test
    fun testFailoverAndFailbackLifecycle() = runTest {
        val prober = FakeSocketProber()
        val manager = NetworkMeshManagerImpl(
            scope = this,
            socketProber = prober
        )

        // 1. Initial: Tailscale OK
        prober.setRule("100.111.123.93", ProbeResult(isReachable = true, latencyMs = 12L))
        prober.setRule("192.168.1.161", ProbeResult(isReachable = true, latencyMs = 2L))
        var state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state.status)

        // 2. Tailscale drops -> Failover to LAN
        prober.setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 1500L))
        state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_LAN, state.status)
        assertEquals("http://192.168.1.161:8899", state.activeBaseUrl)

        // 3. Tailscale recovers -> Failback to Tailscale (Zero-Trust priority)
        prober.setRule("100.111.123.93", ProbeResult(isReachable = true, latencyMs = 14L))
        state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state.status)
        assertEquals("http://100.111.123.93:8899", state.activeBaseUrl)

        // 4. LAN drops while on Tailscale -> Stays on Tailscale
        prober.setRule("192.168.1.161", ProbeResult(isReachable = false, latencyMs = 1500L))
        state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state.status)

        // 5. Tailscale drops while LAN is down -> OFFLINE
        prober.setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 1500L))
        state = manager.probeEndpoints()
        assertEquals(ConnectionStatus.OFFLINE, state.status)
    }

    @Test
    fun testResolveActiveBaseUrlReturnsUrlWhenConnected() = runTest {
        val prober = FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = true, latencyMs = 10L))
        }
        val manager = NetworkMeshManagerImpl(scope = this, socketProber = prober)
        manager.probeEndpoints()

        val url = manager.resolveActiveBaseUrl()
        assertEquals("http://100.111.123.93:8899", url)
    }

    @Test
    fun testResolveActiveBaseUrlProbesWhenOfflineAndSucceeds() = runTest {
        val prober = FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 1000L))
            setRule("192.168.1.161", ProbeResult(isReachable = true, latencyMs = 5L))
        }
        val manager = NetworkMeshManagerImpl(scope = this, socketProber = prober)
        // Manager starts OFFLINE without prior probe
        val url = manager.resolveActiveBaseUrl()
        assertEquals("http://192.168.1.161:8899", url)
    }

    @Test
    fun testResolveActiveBaseUrlThrowsWhenBothEndpointsOffline() = runTest {
        val prober = FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 1000L))
            setRule("192.168.1.161", ProbeResult(isReachable = false, latencyMs = 1000L))
        }
        val manager = NetworkMeshManagerImpl(scope = this, socketProber = prober)

        try {
            manager.resolveActiveBaseUrl()
            fail("Expected IOException when endpoints are offline")
        } catch (e: IOException) {
            assertTrue(e.message?.contains("OFFLINE") == true)
        }
    }

    @Test
    fun testExponentialBackoffFormulaAndBounds() {
        val manager = NetworkMeshManagerImpl(
            scope = TestScope(),
            socketProber = FakeSocketProber(),
            baseBackoffMs = 1000L,
            backoffMultiplier = 1.5,
            maxBackoffMs = 30000L,
            jitterFactor = 0.15,
            randomProvider = { 0.5 } // Midpoint jitter (0% deviation from expected)
        )

        // Attempt 0: 1000 * 1.5^0 = 1000
        assertEquals(1000L, manager.computeBackoffDelay(0))
        // Attempt 1: 1000 * 1.5^1 = 1500
        assertEquals(1500L, manager.computeBackoffDelay(1))
        // Attempt 2: 1000 * 1.5^2 = 2250
        assertEquals(2250L, manager.computeBackoffDelay(2))
        // Attempt 3: 1000 * 1.5^3 = 3375
        assertEquals(3375L, manager.computeBackoffDelay(3))
        // Attempt 4: 1000 * 1.5^4 = 5062
        assertEquals(5062L, manager.computeBackoffDelay(4))
        // Attempt 5: 1000 * 1.5^5 = 7593
        assertEquals(7593L, manager.computeBackoffDelay(5))
        // Attempt 8: 1000 * 1.5^8 = 25628
        assertEquals(25628L, manager.computeBackoffDelay(8))
        // Attempt 9: 1000 * 1.5^9 = 38443 -> capped at 30000
        assertEquals(30000L, manager.computeBackoffDelay(9))
        // Attempt 20: capped at 30000
        assertEquals(30000L, manager.computeBackoffDelay(20))
    }

    @Test
    fun testJitterBoundsVerification() {
        // Minimum jitter test (rnd = 0.0 -> -15%)
        val minManager = NetworkMeshManagerImpl(
            scope = TestScope(),
            socketProber = FakeSocketProber(),
            baseBackoffMs = 1000L,
            jitterFactor = 0.15,
            randomProvider = { 0.0 }
        )
        assertEquals(850L, minManager.computeBackoffDelay(0))
        assertEquals(25500L, minManager.computeBackoffDelay(10))

        // Maximum jitter test (rnd = 1.0 -> +15%)
        val maxManager = NetworkMeshManagerImpl(
            scope = TestScope(),
            socketProber = FakeSocketProber(),
            baseBackoffMs = 1000L,
            jitterFactor = 0.15,
            randomProvider = { 1.0 }
        )
        assertEquals(1150L, maxManager.computeBackoffDelay(0))
        assertEquals(34500L, maxManager.computeBackoffDelay(10))
    }

    @Test
    fun testTriggerImmediateCheckInterruptsDelay() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val prober = FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 100L))
            setRule("192.168.1.161", ProbeResult(isReachable = false, latencyMs = 100L))
        }

        val manager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = dispatcher,
            socketProber = prober,
            baseBackoffMs = 10000L // 10s base backoff
        )

        manager.startMonitoring()
        runCurrent() // Initial probe runs -> OFFLINE, enters 10s delay

        val initialProbeCount = prober.invocationHistory.size
        assertTrue(initialProbeCount >= 2)

        // Advance only 1 second (delay is 10s, so timer has not expired)
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(initialProbeCount, prober.invocationHistory.size)

        // Switch prober to UP
        prober.setRule("100.111.123.93", ProbeResult(isReachable = true, latencyMs = 10L))

        // Trigger immediate check!
        manager.triggerImmediateCheck()
        runCurrent()

        // Should have probed immediately without waiting for remaining 9 seconds
        assertTrue(prober.invocationHistory.size > initialProbeCount)
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, manager.endpointState.value.status)

        manager.stopMonitoring()
        manager.close()
    }
}
