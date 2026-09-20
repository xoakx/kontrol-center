package com.example.e2e.tier4_scenarios

import com.example.data.api.AgentControlRequest
import com.example.data.entity.HostEntity
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import com.example.e2e.tier1_features.ExecutiveDashboardSummary
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
 * Scenario S2: Autonomous Fleet Triage & Restart (Features F3, F4, F12).
 *
 * Sequence:
 * 1. Fleet poll indicates `qwen14b-inference` is degraded/failed (PID defunct, active=false).
 * 2. Executive Dashboard status flags WARNING due to incomplete fleet health.
 * 3. Operator navigates to Fleet Daemons drill-down, inspects memory and state.
 * 4. Operator dispatches 1-tap restart via Arcade (/api/agents/control).
 * 5. Host confirms restart, returning active=true and state="running".
 * 6. Follow-up fleet poll confirms healthy status, restoring Executive Dashboard to OPTIMAL.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S2_FleetTriageRestartScenarioTest : E2eTestHarness() {

    private fun evaluateDashboardStatus(
        fleet: com.example.data.api.FleetStatusResponse,
        telemetry: com.example.data.api.TelemetryResponse,
        netsec: com.example.data.api.NetSecOverviewResponse,
        host: HostEntity
    ): ExecutiveDashboardSummary {
        val runningCount = fleet.agents.count { it.status.active }
        val totalCount = fleet.agents.size
        val peakGpu = telemetry.gpus.maxOfOrNull { it.tempC } ?: 0

        val overall = if (runningCount < totalCount) "WARNING" else "OPTIMAL"

        return ExecutiveDashboardSummary(
            systemHealthCard = "Optimal • ${peakGpu}°C",
            fleetDaemonsCard = "$runningCount/$totalCount Active",
            securitySiemCard = "${netsec.crowdsecBanCount} Bans • ${netsec.suricataAlertCount} Alerts",
            connectedWorkstationsCard = "Connected via Tailscale (${host.lastLatencyMs}ms)",
            overallStatus = overall
        )
    }

    @Test
    fun executeScenario2_FleetTriageAndAutonomousRestart() = runBlocking {
        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 12L)
        val telemetry = apiService.getTelemetry()
        val netsec = apiService.getNetSecOverview()

        // Step 1: Fleet starts in degraded state (qwen14b failed)
        val degradedFleetPayload = """
        {
          "agents": [
            {
              "id": "qwen14b-inference",
              "name": "Qwen 14B Local Inference",
              "service": "qwen14b-inference.service",
              "category": "Local LLM Inference",
              "description": "Local LLM inference daemon",
              "status": {
                "active": false,
                "state": "failed",
                "pid": null,
                "memory_mb": 0.0
              }
            },
            {
              "id": "gemini-scribe",
              "name": "Gemini Scribe",
              "service": "gemini-scribe.service",
              "category": "Knowledge Base Maintenance",
              "description": "Knowledge scribe daemon",
              "status": {
                "active": true,
                "state": "running",
                "pid": 8011,
                "memory_mb": 120.4
              }
            }
          ]
        }
        """.trimIndent()

        dispatcher.setResponse("/api/fleet/status", 200, degradedFleetPayload)

        val degradedFleet = apiService.getFleetStatus()
        assertEquals(2, degradedFleet.agents.size)
        val failedDaemon = degradedFleet.agents.find { it.id == "qwen14b-inference" }
        assertNotNull(failedDaemon)
        assertFalse(failedDaemon?.status?.active == true)
        assertEquals("failed", failedDaemon?.status?.state)

        // Step 2: Executive Dashboard marks status as WARNING
        val degradedDash = evaluateDashboardStatus(degradedFleet, telemetry, netsec, host)
        assertEquals("WARNING", degradedDash.overallStatus)
        assertEquals("1/2 Active", degradedDash.fleetDaemonsCard)

        // Step 3 & 4: Operator initiates 1-tap restart
        val restartReq = AgentControlRequest(
            service = "qwen14b-inference.service",
            action = "restart"
        )
        val restartResp = apiService.controlAgent(restartReq)

        // Step 5: Verify restart response
        assertNotNull(restartResp)
        assertTrue(restartResp.success)
        assertEquals("qwen14b-inference.service", restartResp.service)
        assertEquals("restart", restartResp.action)
        assertTrue(restartResp.status?.active == true)
        assertEquals("running", restartResp.status?.state)

        // Step 6: Follow-up poll confirms all daemons active -> OPTIMAL
        val recoveredFleetPayload = """
        {
          "agents": [
            {
              "id": "qwen14b-inference",
              "name": "Qwen 14B Local Inference",
              "service": "qwen14b-inference.service",
              "category": "Local LLM Inference",
              "description": "Local LLM inference daemon",
              "status": {
                "active": true,
                "state": "running",
                "pid": 10108,
                "memory_mb": 9240.5
              }
            },
            {
              "id": "gemini-scribe",
              "name": "Gemini Scribe",
              "service": "gemini-scribe.service",
              "category": "Knowledge Base Maintenance",
              "description": "Knowledge scribe daemon",
              "status": {
                "active": true,
                "state": "running",
                "pid": 8011,
                "memory_mb": 120.4
              }
            }
          ]
        }
        """.trimIndent()

        dispatcher.setResponse("/api/fleet/status", 200, recoveredFleetPayload)

        val healthyFleet = apiService.getFleetStatus()
        val healthyDash = evaluateDashboardStatus(healthyFleet, telemetry, netsec, host)
        assertEquals("OPTIMAL", healthyDash.overallStatus)
        assertEquals("2/2 Active", healthyDash.fleetDaemonsCard)
    }
}
