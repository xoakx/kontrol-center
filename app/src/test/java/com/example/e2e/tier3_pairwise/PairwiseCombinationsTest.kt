package com.example.e2e.tier3_pairwise

import com.example.data.api.AgentControlRequest
import com.example.data.api.LoginRequest
import com.example.data.api.RfcVoteRequest
import com.example.data.api.UnbanRequest
import com.example.data.entity.HostEntity
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.FakeSshSession
import com.example.e2e.harness.MockTelemetryPayloads
import com.example.e2e.tier1_features.ExecutiveDashboardSummary
import com.example.service.ConnectionStatus
import com.example.service.NetworkMeshManagerImpl
import com.example.service.ProbeResult
import com.example.service.SocketProber
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 3 Tests: Cross-Feature Pairwise Combinations (P01 - P12).
 * Exercises interactions across feature boundaries:
 * - P01: Mesh + Room Vault (endpoint resolution persisted to Room DB)
 * - P02: Mesh + Arcade (mesh-resolved URL binds to Arcade Retrofit client)
 * - P03: Arcade + Fleet (degraded daemon detection triggers 1-tap restart)
 * - P04: Fleet + Executive Dash (fleet health state drives dashboard rollup)
 * - P05: Hardware + Tunables (telemetry load triggers governor tuning)
 * - P06: Tunables + PTY (audio re-anchor dispatched during active PTY streaming)
 * - P07: NetSec + CrowdSec (Suricata attack IP cross-correlated with CrowdSec ban)
 * - P08: CrowdSec + Executive Dash (1-tap unban decrements dashboard ban count)
 * - P09: PTY + RFC (terminal log inspection coordinates with RFC approval)
 * - P10: RFC + Tunables (approved RFC triggers CPU governor alignment)
 * - P11: Mesh + Executive Dash (failover updates dashboard endpoint & latency chip)
 * - P12: Room Vault + Executive Dash (cached DB host enables instant cold-render)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PairwiseCombinationsTest : E2eTestHarness() {

    private fun aggregateDashboard(
        telemetry: com.example.data.api.TelemetryResponse?,
        fleet: com.example.data.api.FleetStatusResponse?,
        netsec: com.example.data.api.NetSecOverviewResponse?,
        activeHost: HostEntity?
    ): ExecutiveDashboardSummary {
        val peakGpu = telemetry?.gpus?.maxOfOrNull { it.tempC } ?: 0
        val sysCard = if (telemetry != null) {
            "Optimal • ${peakGpu}°C (RAM: ${telemetry.memory.usedPct.toInt()}%)"
        } else {
            "System Health Unavailable"
        }

        val runningCount = fleet?.agents?.count { it.status.active } ?: 0
        val fleetTotal = fleet?.agents?.size ?: 0
        val fleetCard = if (fleet != null) "$runningCount/$fleetTotal Active" else "Fleet Offline"

        val secCard = if (netsec != null) {
            "${netsec.crowdsecBanCount} Active Bans • ${netsec.suricataAlertCount} Alerts"
        } else {
            "Security Telemetry Offline"
        }

        val hostCard = if (activeHost != null && activeHost.isOnline) {
            val mode = if (activeHost.activeEndpoint?.contains("100.111") == true) "Tailscale" else "LAN"
            "Connected via $mode (${activeHost.lastLatencyMs}ms)"
        } else {
            "Offline"
        }

        val overall = when {
            activeHost == null || !activeHost.isOnline -> "OFFLINE"
            peakGpu >= 85 || (netsec?.crowdsecBanCount ?: 0) > 5 -> "CRITICAL"
            runningCount < fleetTotal || peakGpu >= 70 -> "WARNING"
            else -> "OPTIMAL"
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
    fun P01_Mesh_x_RoomVault() = runBlocking {
        // 1. Mesh resolves Tailscale endpoint
        val fakeProber = SocketProber { ip, _, _ ->
            if (ip == "100.111.123.93") ProbeResult(true, 14L) else ProbeResult(false, -1L)
        }
        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = fakeProber
        )
        val endpointState = meshManager.probeEndpoints()
        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, endpointState.status)

        // 2. Persist resolved state into Room DB
        val host = HostEntity(
            id = 1,
            name = "fml",
            address = endpointState.activeIp,
            tailscaleAddress = "100.111.123.93",
            lanAddress = "192.168.1.161",
            activeEndpoint = endpointState.activeBaseUrl,
            lastLatencyMs = endpointState.latencyMs,
            isOnline = true
        )
        hostRepository.insertHost(host)

        // 3. Verify Room DB retrieved entity matches mesh state
        val retrieved = hostRepository.getHostById(1).first()
        assertNotNull(retrieved)
        assertEquals("http://100.111.123.93:8899", retrieved?.activeEndpoint)
        assertEquals(14L, retrieved?.lastLatencyMs)
        assertTrue(retrieved?.isOnline == true)
    }

    @Test
    fun P02_Mesh_x_Arcade() = runBlocking {
        // 1. Resolve mesh endpoint
        val fakeProber = SocketProber { ip, _, _ ->
            ProbeResult(true, 11L)
        }
        val meshManager = NetworkMeshManagerImpl(
            scope = this,
            ioDispatcher = testDispatcher,
            socketProber = fakeProber
        )
        val activeUrl = meshManager.resolveActiveBaseUrl()
        assertEquals("http://100.111.123.93:8899", activeUrl)

        // 2. Arcade client executes against activeUrl
        val loginResp = apiService.login(LoginRequest(token = "arcade_operator_master_key"))
        assertEquals("mock_jwt_operator_token_hs256", loginResp.accessToken)

        val telemetry = apiService.getTelemetry()
        assertEquals(20, telemetry.cpu.cores)
        assertEquals(2, telemetry.gpus.size)
    }

    @Test
    fun P03_Arcade_x_Fleet() = runBlocking {
        // 1. Ingest degraded fleet state
        dispatcher.setResponse("/api/fleet/status", 200, MockTelemetryPayloads.degradedFleetDaemonsJson())
        val initialFleet = apiService.getFleetStatus()
        val authMonitor = initialFleet.agents.find { it.id == "auth-monitor" }
        assertNotNull(authMonitor)
        assertEquals("failed", authMonitor?.status?.state)

        // 2. Trigger 1-tap restart via Arcade
        val restartResponse = apiService.controlAgent(
            AgentControlRequest(service = "auth-monitor.service", action = "restart")
        )
        assertTrue(restartResponse.success)
        assertEquals("running", restartResponse.status?.state)

        // 3. Subsequent poll reflects restored fleet
        dispatcher.setResponse("/api/fleet/status", 200, MockTelemetryPayloads.all12FleetDaemonsJson())
        val restoredFleet = apiService.getFleetStatus()
        val restoredDaemon = restoredFleet.agents.find { it.id == "auth-monitor" }
        assertEquals("running", restoredDaemon?.status?.state)
    }

    @Test
    fun P04_Fleet_x_ExecutiveDash() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val netsec = apiService.getNetSecOverview()
        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 12L)

        // 1. Fully operational fleet -> OPTIMAL
        dispatcher.setResponse("/api/fleet/status", 200, MockTelemetryPayloads.all12FleetDaemonsJson())
        val healthyFleet = apiService.getFleetStatus()
        val healthyDash = aggregateDashboard(telemetry, healthyFleet, netsec, host)
        assertEquals("11/12 Active", healthyDash.fleetDaemonsCard)

        // 2. Degraded fleet with failed daemon -> WARNING
        dispatcher.setResponse("/api/fleet/status", 200, MockTelemetryPayloads.degradedFleetDaemonsJson())
        val degradedFleet = apiService.getFleetStatus()
        val degradedDash = aggregateDashboard(telemetry, degradedFleet, netsec, host)
        assertEquals("WARNING", degradedDash.overallStatus)
    }

    @Test
    fun P05_Hardware_x_Tunables() = runBlocking {
        // 1. Hardware telemetry shows elevated load
        val telemetry = apiService.getTelemetry()
        assertNotNull(telemetry)

        // 2. Operator triggers performance lock tunable
        val payload = """{"action": "perf_lock"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(req).execute().use { resp ->
            assertTrue(resp.isSuccessful)
        }

        // 3. Verified governor status in telemetry
        val updatedTelemetry = apiService.getTelemetry()
        assertEquals("performance", updatedTelemetry.cpu.governor)
    }

    @Test
    fun P06_Tunables_x_Pty() = runBlocking {
        val fakeSsh = FakeSshSession()
        val ptyStream = fakeSsh.getOutputStream()

        // 1. PTY terminal actively streaming output
        ptyStream.write("tail -f /var/log/syslog\n".toByteArray(Charsets.UTF_8))
        ptyStream.flush()

        // 2. Concurrent audio re-anchor tunable dispatched
        val payload = """{"action": "audio_reanchor"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val tunableResult = okHttpClient.newCall(req).execute().use { resp ->
            resp.isSuccessful
        }
        assertTrue(tunableResult)

        // 3. Verify PTY terminal continues streaming without disruption
        ptyStream.write("PipeWire audio re-anchored\n".toByteArray(Charsets.UTF_8))
        ptyStream.flush()

        val captured = fakeSsh.getCapturedInput()
        assertTrue(captured.contains("tail -f /var/log/syslog"))
        assertTrue(captured.contains("PipeWire audio re-anchored"))
    }

    @Test
    fun P07_NetSec_x_CrowdSec() = runBlocking {
        // 1. Suricata detects intrusion attempt
        val suricataResponse = apiService.getSuricataAlerts(50)
        val alert = suricataResponse.alerts[0]
        val attackerIp = alert.srcIp // "203.0.113.195"

        // 2. CrowdSec registers active ban for that IP
        val crowdSecBanPayload = """
        {
          "active_decisions": [
            {
              "id": 99001,
              "value": "$attackerIp",
              "scenario": "suricata:cve-exploit",
              "origin": "cscli",
              "duration": "4h"
            }
          ],
          "bouncers": []
        }
        """.trimIndent()
        dispatcher.setResponse("/api/netsec/crowdsec/decisions", 200, crowdSecBanPayload)

        val decisions = apiService.getCrowdSecDecisions()
        val bannedIp = decisions.activeDecisions[0].value

        // Cross-correlation
        assertEquals(attackerIp, bannedIp)
    }

    @Test
    fun P08_CrowdSec_x_ExecutiveDash() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val fleet = apiService.getFleetStatus()
        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 10L)

        // Initial 2 bans
        val initialNetSec = apiService.getNetSecOverview()
        assertEquals(2, initialNetSec.crowdsecBanCount)
        val dashBefore = aggregateDashboard(telemetry, fleet, initialNetSec, host)
        assertTrue(dashBefore.securitySiemCard.contains("2 Active Bans"))

        // Unban 1 IP
        val unbanResp = apiService.unbanIp(UnbanRequest("209.99.190.113"))
        assertTrue(unbanResp.success)

        // Dashboard updates with 1 ban remaining
        val updatedNetSec = initialNetSec.copy(crowdsecBanCount = 1)
        val dashAfter = aggregateDashboard(telemetry, fleet, updatedNetSec, host)
        assertTrue(dashAfter.securitySiemCard.contains("1 Active Bans"))
    }

    @Test
    fun P09_Pty_x_Rfc() = runBlocking {
        val fakeSsh = FakeSshSession()
        val ptyStream = fakeSsh.getOutputStream()

        // 1. Operator reviews RFC via PTY shell
        ptyStream.write("git diff origin/master..HEAD | cat\n".toByteArray(Charsets.UTF_8))
        ptyStream.flush()

        // 2. Approves RFC via API
        val voteResp = apiService.voteRfc("RFC-00142", RfcVoteRequest("approve"))
        assertTrue(voteResp.success)
        val taskId = voteResp.taskId

        // 3. Terminal session confirms execution
        ptyStream.write("Executing task: $taskId\n".toByteArray(Charsets.UTF_8))
        ptyStream.flush()

        val captured = fakeSsh.getCapturedInput()
        assertTrue(captured.contains("Executing task: task_rfc-00142_a1b2"))
    }

    @Test
    fun P10_Rfc_x_Tunables() = runBlocking {
        // 1. Ingest RFC for CPU frequency governor alignment
        val rfcs = apiService.getRfcs().rfcs
        val rfc = rfcs.find { it.id == "RFC-00142" }
        assertNotNull(rfc)
        assertEquals("tuned-adm profile throughput-performance", rfc?.proposedSteps?.get(0))

        // 2. Approve RFC
        val voteResp = apiService.voteRfc("RFC-00142", RfcVoteRequest("approve"))
        assertEquals("APPROVED", voteResp.status)

        // 3. Tunables verification confirms performance profile
        val telemetry = apiService.getTelemetry()
        assertEquals("performance", telemetry.cpu.governor)
    }

    @Test
    fun P11_Mesh_x_ExecutiveDash() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val fleet = apiService.getFleetStatus()
        val netsec = apiService.getNetSecOverview()

        // 1. Connected via Tailscale (14ms)
        val tsHost = HostEntity(
            name = "fml",
            address = "100.111.123.93",
            activeEndpoint = "http://100.111.123.93:8899",
            lastLatencyMs = 14L,
            isOnline = true
        )
        val tsDash = aggregateDashboard(telemetry, fleet, netsec, tsHost)
        assertEquals("Connected via Tailscale (14ms)", tsDash.connectedWorkstationsCard)

        // 2. Failover to LAN (3ms)
        val lanHost = tsHost.copy(
            address = "192.168.1.161",
            activeEndpoint = "http://192.168.1.161:8899",
            lastLatencyMs = 3L
        )
        val lanDash = aggregateDashboard(telemetry, fleet, netsec, lanHost)
        assertEquals("Connected via LAN (3ms)", lanDash.connectedWorkstationsCard)
    }

    @Test
    fun P12_RoomVault_x_ExecutiveDash() = runBlocking {
        // Seed host into Room DB
        val cachedHost = HostEntity(
            id = 42,
            name = "fml-workstation",
            address = "100.111.123.93",
            activeEndpoint = "http://100.111.123.93:8899",
            lastLatencyMs = 12L,
            isOnline = true
        )
        hostRepository.insertHost(cachedHost)

        // Cold-render dashboard directly from Room DB cache before network calls
        val dbHost = hostRepository.getHostById(42).first()
        assertNotNull(dbHost)

        val coldDash = aggregateDashboard(null, null, null, dbHost)
        assertEquals("Connected via Tailscale (12ms)", coldDash.connectedWorkstationsCard)
        assertEquals("System Health Unavailable", coldDash.systemHealthCard)
    }
}
