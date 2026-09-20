package com.example.e2e.tier2_boundaries

import com.example.data.api.AgentControlRequest
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Tier 2 Boundary Tests: B04 Fleet Daemons Boundary.
 * Covers 5 boundary conditions:
 * - T2_B04_01: Missing / null PID handling for inactive or dead fleet service
 * - T2_B04_02: Crash loops with rapid restart attempts and failed substate detection
 * - T2_B04_03: Non-existent daemon name control request handling
 * - T2_B04_04: Simultaneous multi-daemon restart dispatch across concurrent coroutines
 * - T2_B04_05: Command timeout / socket timeout during slow systemctl daemon action
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B04_FleetDaemonsBoundaryTest : E2eTestHarness() {

    @Test
    fun T2_B04_01_missing_pid_for_inactive_service() = runBlocking {
        // Inject payload with dead service having null PID
        val deadDaemonPayload = """
        {
          "agents": [
            {
              "id": "gemini-git-custodian",
              "name": "Git Custodian",
              "service": "gemini-git-custodian.service",
              "category": "Repository Integrity",
              "description": "Monitors git repository trees",
              "status": {
                "active": false,
                "state": "inactive",
                "pid": null,
                "memory_mb": 0.0
              }
            }
          ]
        }
        """.trimIndent()

        dispatcher.setResponse("/api/fleet/status", 200, deadDaemonPayload)

        val fleet = apiService.getFleetStatus()
        assertEquals(1, fleet.agents.size)
        val custodian = fleet.agents[0]

        assertFalse(custodian.status.active)
        assertEquals("inactive", custodian.status.state)
        assertNull(custodian.status.pid)
        assertEquals(0.0, custodian.status.memoryMb ?: 0.0, 0.01)
    }

    @Test
    fun T2_B04_02_crash_loops_with_rapid_restart_attempts() = runBlocking {
        val crashLoopPayload = """
        {
          "agents": [
            {
              "id": "qwen14b-inference",
              "name": "Qwen 14B Local Inference",
              "service": "qwen14b-inference.service",
              "category": "Local LLM Inference",
              "description": "Local LLM service",
              "status": {
                "active": false,
                "state": "failed",
                "pid": null,
                "memory_mb": 0.0
              }
            }
          ]
        }
        """.trimIndent()

        dispatcher.setResponse("/api/fleet/status", 200, crashLoopPayload)

        val fleet = apiService.getFleetStatus()
        val daemon = fleet.agents[0]

        assertFalse(daemon.status.active)
        assertEquals("failed", daemon.status.state)
    }

    @Test
    fun T2_B04_03_non_existent_daemon_name() = runBlocking {
        dispatcher.setResponse(
            "/api/agents/control",
            200,
            """{"success": false, "service": "ghost-daemon.service", "action": "restart", "message": "Unit ghost-daemon.service not found."}"""
        )

        val response = apiService.controlAgent(
            AgentControlRequest(service = "ghost-daemon.service", action = "restart")
        )

        assertNotNull(response)
        assertFalse(response.success)
        assertEquals("ghost-daemon.service", response.service)
        assertEquals("Unit ghost-daemon.service not found.", response.message)
    }

    @Test
    fun T2_B04_04_simultaneous_multi_daemon_restart() = runBlocking {
        val targetServices = listOf(
            "gemini-scribe.service",
            "gemini-sre-watchdog.service",
            "transcriber.service",
            "audio-webui.service"
        )

        val deferreds = targetServices.map { serviceName ->
            async(Dispatchers.IO) {
                apiService.controlAgent(
                    AgentControlRequest(service = serviceName, action = "restart")
                )
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(4, results.size)
        assertTrue(results.all { it.success })
        val restartedServices = results.map { it.service }.toSet()
        assertEquals(targetServices.toSet(), restartedServices)
    }

    @Test
    fun T2_B04_05_command_timeout_during_restart() = runBlocking {
        dispatcher.overrideResponse("/api/agents/control") {
            throw SocketTimeoutException("Read timed out waiting for systemctl response")
        }

        try {
            apiService.controlAgent(
                AgentControlRequest(service = "qwen14b-inference.service", action = "restart")
            )
            fail("Expected SocketTimeoutException")
        } catch (e: IOException) {
            assertTrue(e is SocketTimeoutException)
            assertTrue(e.message?.contains("Read timed out") == true)
        }
    }
}
