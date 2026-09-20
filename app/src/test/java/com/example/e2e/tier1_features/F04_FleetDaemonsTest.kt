package com.example.e2e.tier1_features

import com.example.data.api.AgentControlRequest
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1 Tests for Feature 4: 12 Fleet Daemons Monitoring & Control.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F04_01: Ingestion and discovery of all 12 registered fleet daemons
 * - T1_F04_02: 1-Tap restart daemon control mutation
 * - T1_F04_03: 1-Tap stop daemon control mutation
 * - T1_F04_04: Standby classification for non-running periodic daemons
 * - T1_F04_05: Fleet aggregate health rollup logic and degraded state detection
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F04_FleetDaemonsTest : E2eTestHarness() {

    private val expectedDaemonIds = setOf(
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

    @Test
    fun T1_F04_01_parse_all_12_fleet_daemons() = runBlocking {
        val fleetResponse = apiService.getFleetStatus()
        assertNotNull(fleetResponse)
        assertEquals(12, fleetResponse.agents.size)

        val retrievedIds = fleetResponse.agents.map { it.id }.toSet()
        assertEquals(expectedDaemonIds, retrievedIds)

        // Verify detailed fields on a primary daemon
        val qwen = fleetResponse.agents.find { it.id == "qwen14b-inference" }
        assertNotNull(qwen)
        assertEquals("qwen14b-inference.service", qwen?.service)
        assertTrue(qwen?.status?.active == true)
        assertEquals("running", qwen?.status?.state)
        assertEquals(10108L, qwen?.status?.pid)
        assertEquals(9240.5, qwen?.status?.memoryMb ?: 0.0, 0.1)
    }

    @Test
    fun T1_F04_02_1tap_restart_agent_control() = runBlocking {
        val request = AgentControlRequest(
            service = "gemini-scribe.service",
            action = "restart"
        )
        val response = apiService.controlAgent(request)
        assertNotNull(response)
        assertTrue(response.success)
        assertEquals("gemini-scribe.service", response.service)
        assertEquals("restart", response.action)
        assertTrue(response.status?.active == true)
        assertEquals("running", response.status?.state)

        // Verify intercepted request
        val lastReq = dispatcher.lastRecordedRequest
        assertNotNull(lastReq)
        assertEquals("/api/agents/control", lastReq?.url?.encodedPath)
    }

    @Test
    fun T1_F04_03_1tap_stop_agent_control() = runBlocking {
        val request = AgentControlRequest(
            service = "transcriber.service",
            action = "stop"
        )
        val response = apiService.controlAgent(request)
        assertNotNull(response)
        assertTrue(response.success)
        assertEquals("stop", response.action)
        assertFalse(response.status?.active == true)
        assertEquals("stopped", response.status?.state)
    }

    @Test
    fun T1_F04_04_intentional_standby_handling() = runBlocking {
        val fleetResponse = apiService.getFleetStatus()
        val custodian = fleetResponse.agents.find { it.id == "gemini-git-custodian" }
        assertNotNull(custodian)
        assertFalse(custodian?.status?.active == true)
        assertEquals("stopped", custodian?.status?.state)
        assertEquals("Repository Integrity", custodian?.category)
    }

    @Test
    fun T1_F04_05_fleet_health_aggregation() = runBlocking {
        // Inject degraded payload with auth-monitor failed
        dispatcher.setResponse("/api/fleet/status", 200, MockTelemetryPayloads.degradedFleetDaemonsJson())

        val fleetResponse = apiService.getFleetStatus()
        assertEquals(12, fleetResponse.agents.size)

        val runningCount = fleetResponse.agents.count { it.status.active }
        val failedCount = fleetResponse.agents.count { it.status.state == "failed" }

        assertEquals(11, runningCount)
        assertEquals(1, failedCount)

        val failedDaemon = fleetResponse.agents.find { it.status.state == "failed" }
        assertEquals("auth-monitor", failedDaemon?.id)
    }
}
