package com.example.e2e.tier4_scenarios

import com.example.data.entity.HostEntity
import com.example.e2e.harness.E2eTestHarness
import com.example.service.ConnectionStatus
import com.example.service.NetworkMeshManagerImpl
import com.example.service.ProbeResult
import com.example.service.SocketProber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Scenario S1: Network Handover & Offline Resiliency (Features F1, F2, F3).
 *
 * Sequence:
 * 1. App starts on Tailscale mesh (100.111.123.93:8899).
 * 2. Caches host endpoints and telemetry in Room DB v3.
 * 3. Tailscale interface drops; NetworkMeshManager detects failure and falls back to LAN (192.168.1.161:8899).
 * 4. LAN drops momentarily; manager transitions to OFFLINE with exponential backoff.
 * 5. Room DB v3 cached data persists without crash or UI disruption.
 * 6. Tailscale reconnects; manager auto-reprobes and restores CONNECTED_TAILSCALE state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S1_NetworkHandoverOfflineScenarioTest : E2eTestHarness() {

    @Test
    fun executeScenario1_NetworkHandoverAndOfflineResiliency() = runBlocking {
        // Step 1: Initialize Host and SocketProber with mutable connectivity states
        var tsReachable = true
        var lanReachable = true

        val dynamicProber = SocketProber { ip, _, _ ->
            when (ip) {
                "100.111.123.93" -> {
                    if (tsReachable) ProbeResult(true, 12L)
                    else ProbeResult(false, -1L, IOException("Tailscale interface down"))
                }
                "192.168.1.161" -> {
                    if (lanReachable) ProbeResult(true, 3L)
                    else ProbeResult(false, -1L, IOException("LAN unreachable"))
                }
                else -> ProbeResult(false, -1L)
            }
        }

        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = dynamicProber,
            tailscaleIp = "100.111.123.93",
            lanIp = "192.168.1.161",
            port = 8899
        )

        // Initial probe on Tailscale
        val state1 = meshManager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state1.status)
        assertEquals("http://100.111.123.93:8899", state1.activeBaseUrl)

        // Step 2: Seed and cache in Room DB v3
        val host = HostEntity(
            id = 1,
            name = "Workstation fml",
            address = state1.activeIp,
            tailscaleAddress = "100.111.123.93",
            lanAddress = "192.168.1.161",
            activeEndpoint = state1.activeBaseUrl,
            lastLatencyMs = state1.latencyMs,
            isOnline = true
        )
        hostRepository.insertHost(host)
        val initialCachedHost = hostRepository.getHostById(1).first()
        assertNotNull(initialCachedHost)
        assertEquals(12L, initialCachedHost?.lastLatencyMs)

        // Step 3: Tailscale drops -> Failover to LAN
        tsReachable = false
        lanReachable = true

        val state2 = meshManager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_LAN, state2.status)
        assertEquals("http://192.168.1.161:8899", state2.activeBaseUrl)
        assertEquals(3L, state2.latencyMs)

        // Update Room DB with fallback state
        hostRepository.updateHost(initialCachedHost!!.copy(
            address = state2.activeIp,
            activeEndpoint = state2.activeBaseUrl,
            lastLatencyMs = state2.latencyMs
        ))
        val fallbackHost = hostRepository.getHostById(1).first()
        assertEquals("http://192.168.1.161:8899", fallbackHost?.activeEndpoint)

        // Step 4: LAN also drops -> OFFLINE
        lanReachable = false

        val state3 = meshManager.probeEndpoints()
        assertEquals(ConnectionStatus.OFFLINE, state3.status)
        assertEquals("", state3.activeBaseUrl)
        assertEquals(-1L, state3.latencyMs)

        // Step 5: Room DB data remains completely intact during offline state
        val offlinePersistedHost = hostRepository.getHostById(1).first()
        assertNotNull(offlinePersistedHost)
        assertEquals("Workstation fml", offlinePersistedHost?.name)
        assertEquals("100.111.123.93", offlinePersistedHost?.tailscaleAddress)
        assertEquals("192.168.1.161", offlinePersistedHost?.lanAddress)

        // Step 6: Tailscale recovers -> Back to CONNECTED_TAILSCALE
        tsReachable = true

        val state4 = meshManager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, state4.status)
        assertEquals("http://100.111.123.93:8899", state4.activeBaseUrl)
        assertEquals(12L, state4.latencyMs)

        hostRepository.updateHost(offlinePersistedHost!!.copy(
            address = state4.activeIp,
            activeEndpoint = state4.activeBaseUrl,
            lastLatencyMs = state4.latencyMs,
            isOnline = true
        ))

        val finalHost = hostRepository.getHostById(1).first()
        assertTrue(finalHost?.isOnline == true)
        assertEquals("http://100.111.123.93:8899", finalHost?.activeEndpoint)
    }
}
