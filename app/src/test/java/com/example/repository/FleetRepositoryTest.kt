package com.example.repository

import com.example.data.api.AgentControlRequest
import com.example.data.entity.HostEntity
import com.example.data.repository.FleetRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FleetRepositoryTest : E2eTestHarness() {

    private lateinit var fleetRepository: FleetRepository

    @Before
    override fun setUp() {
        super.setUp()
        fleetRepository = FleetRepository(apiService = apiService)
    }

    @Test
    fun testGetFleetStatus_discoversAll12SupervisedDaemons() = runBlocking {
        val result = fleetRepository.getFleetStatus()
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(12, response?.agents?.size)

        // Supervised agents flow should also contain 12 daemons
        val supervised = fleetRepository.supervisedAgents.value
        assertEquals(12, supervised.size)
        val ids = supervised.map { it.id }.toSet()
        assertEquals(FleetRepository.SUPERVISED_DAEMON_IDS, ids)

        // Verify primary LLM inference daemon
        val qwen = supervised.find { it.id == "qwen14b-inference" }
        assertNotNull(qwen)
        assertEquals("qwen14b-inference.service", qwen?.service)
        assertTrue(qwen?.status?.active == true)
        assertEquals(10108L, qwen?.status?.pid)
        assertEquals(9240.5, qwen?.status?.memoryMb ?: 0.0, 0.1)
    }

    @Test
    fun testStandbyDaemonClassification_gitCustodianIsStandby() = runBlocking {
        fleetRepository.getFleetStatus()
        val custodian = fleetRepository.supervisedAgents.value.find { it.id == "gemini-git-custodian" }
        assertNotNull(custodian)
        assertTrue(FleetRepository.isStandbyDaemon(custodian!!.id))
        assertFalse(custodian.status.active)
        assertEquals("stopped", custodian.status.state)
    }

    @Test
    fun testControlAgent_restartMutationSuccess() = runBlocking {
        val result = fleetRepository.restartDaemon("gemini-scribe.service")
        assertTrue(result.isSuccess)
        val resp = result.getOrNull()
        assertNotNull(resp)
        assertTrue(resp?.success == true)
        assertEquals("gemini-scribe.service", resp?.service)
        assertEquals("restart", resp?.action)
        assertTrue(resp?.status?.active == true)
    }

    @Test
    fun testControlAgent_stopMutationSuccess() = runBlocking {
        val result = fleetRepository.stopDaemon("transcriber.service")
        assertTrue(result.isSuccess)
        val resp = result.getOrNull()
        assertNotNull(resp)
        assertTrue(resp?.success == true)
        assertEquals("stop", resp?.action)
        assertFalse(resp?.status?.active == true)
    }

    @Test
    fun testDegradedFleetDetection_authMonitorFailed() = runBlocking {
        dispatcher.setResponse("/api/fleet/status", 200, MockTelemetryPayloads.degradedFleetDaemonsJson())

        val result = fleetRepository.getFleetStatus()
        assertTrue(result.isSuccess)

        val agents = fleetRepository.supervisedAgents.value
        val failed = agents.find { it.status.state == "failed" }
        assertNotNull(failed)
        assertEquals("auth-monitor", failed?.id)
        assertFalse(failed?.status?.active == true)
    }

    @Test
    fun testControlAgent_networkFailureWithoutSsh_returnsFailure() = runBlocking {
        dispatcher.simulateNetworkFailure = true
        dispatcher.customFailureException = IOException("Connection refused to Arcade :8899")

        val result = fleetRepository.controlAgent("gemini-scribe.service", "restart", null)
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
    }
}
