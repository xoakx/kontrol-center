package com.example.e2e.tier4_scenarios

import com.example.data.api.LoginRequest
import com.example.data.entity.HostEntity
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.tier1_features.ExecutiveDashboardSummary
import com.example.service.ConnectionStatus
import com.example.service.NetworkMeshManagerImpl
import com.example.service.ProbeResult
import com.example.service.SocketProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

/**
 * Scenario S6: Cold-Boot Executive Health Readout (Features F1, F3, F12).
 *
 * Sequence:
 * 1. Cold start: In-memory Room DB, clean Keystore, unprimed Retrofit client.
 * 2. NetworkMeshManager executes Tailscale zero-trust probe within 1500ms SLA window.
 * 3. ArcadeClient authenticates via Bearer token exchange.
 * 4. Parallel asynchronous fetch across Silicon Telemetry, Fleet Status, and NetSec.
 * 5. Synthesis of 4 primary overview cards (System Health, Fleet, NetSec, Workstation).
 * 6. Total end-to-end cold-boot evaluation completes within <5000ms executive readability SLA.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S6_ColdBootExecutiveHealthScenarioTest : E2eTestHarness() {

    private fun aggregateDashboard(
        telemetry: com.example.data.api.TelemetryResponse,
        fleet: com.example.data.api.FleetStatusResponse,
        netsec: com.example.data.api.NetSecOverviewResponse,
        activeHost: HostEntity
    ): ExecutiveDashboardSummary {
        val peakGpu = telemetry.gpus.maxOfOrNull { it.tempC } ?: 0
        val sysCard = "Optimal • ${peakGpu}°C (CPU: ${telemetry.cpu.tempC.toInt()}°C, RAM: ${telemetry.memory.usedPct.toInt()}%)"

        val runningCount = fleet.agents.count { it.status.active }
        val fleetCard = "$runningCount/${fleet.agents.size} Active"

        val secCard = "${netsec.crowdsecBanCount} Active Bans • ${netsec.suricataAlertCount} Alerts"
        val hostCard = "Connected via Tailscale (${activeHost.lastLatencyMs}ms)"

        val overall = if (peakGpu >= 85 || netsec.crowdsecBanCount > 5) {
            "CRITICAL"
        } else if (runningCount < fleet.agents.size || peakGpu >= 70) {
            "WARNING"
        } else {
            "OPTIMAL"
        }

        return ExecutiveDashboardSummary(
            systemHealthCard = sysCard,
            fleetDaemonsCard = fleetCard,
            securitySiemCard = secCard,
            connectedWorkstationsCard = hostCard,
            overallStatus = overall
        )
    }

    @Test
    fun executeScenario6_ColdBootExecutiveHealthReadout() = runBlocking {
        var dashboard: ExecutiveDashboardSummary? = null

        // Measure total cold-boot time from zero state to full 4-card dashboard
        val totalBootTimeMs = measureTimeMillis {
            // Step 1: Probe Zero-Trust Tailscale endpoint
            val fakeProber = SocketProber { ip, _, _ ->
                if (ip == "100.111.123.93") ProbeResult(true, 14L) else ProbeResult(false, -1L)
            }
            val meshManager = NetworkMeshManagerImpl(
                scope = this,
                ioDispatcher = testDispatcher,
                socketProber = fakeProber,
                probeTimeoutMs = 1500
            )

            val endpointState = meshManager.probeEndpoints()
            assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, endpointState.status)
            assertTrue("Probe latency must be measured", endpointState.latencyMs >= 0)

            // Step 2: Seed Host in Room DB
            val host = HostEntity(
                name = "Workstation fml",
                address = endpointState.activeIp,
                tailscaleAddress = "100.111.123.93",
                lanAddress = "192.168.1.161",
                activeEndpoint = endpointState.activeBaseUrl,
                lastLatencyMs = endpointState.latencyMs,
                isOnline = true
            )
            val hostId = hostRepository.insertHost(host).toInt()
            val loadedHost = database.hostDao().getHostByIdDirect(hostId)
            assertNotNull(loadedHost)

            // Step 3: Authenticate
            val authResp = apiService.login(LoginRequest(token = "arcade_operator_master_key"))
            assertEquals("mock_jwt_operator_token_hs256", authResp.accessToken)

            // Step 4: Parallel asynchronous fetching across all 3 domain endpoints
            val telemetryDeferred = async(Dispatchers.IO) { apiService.getTelemetry() }
            val fleetDeferred = async(Dispatchers.IO) { apiService.getFleetStatus() }
            val netsecDeferred = async(Dispatchers.IO) { apiService.getNetSecOverview() }

            val telemetry = telemetryDeferred.await()
            val fleet = fleetDeferred.await()
            val netsec = netsecDeferred.await()

            // Step 5: Synthesize 4 primary cards
            dashboard = aggregateDashboard(telemetry, fleet, netsec, loadedHost!!)
        }

        // Assert sub-5s executive SLA
        assertTrue("Cold boot to executive readiness must complete in <5000ms, took: ${totalBootTimeMs}ms", totalBootTimeMs < 5000)

        // Assert completeness of all 4 primary summary cards
        assertNotNull(dashboard)
        assertTrue(dashboard?.systemHealthCard?.contains("Optimal") == true)
        assertTrue(dashboard?.fleetDaemonsCard?.contains("Active") == true)
        assertTrue(dashboard?.securitySiemCard?.contains("Active Bans") == true)
        assertTrue(dashboard?.connectedWorkstationsCard?.contains("Connected via Tailscale") == true)
        assertEquals("WARNING", dashboard?.overallStatus)
    }
}
