package com.example.e2e.tier2_boundaries

import com.example.data.entity.HostEntity
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import com.example.e2e.tier1_features.ExecutiveDashboardSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 2 Boundary Tests: B12 Executive Health Dashboard Boundary.
 * Covers 5 boundary conditions:
 * - T2_B12_01: Partial subsystem outage mixed health rollup (hardware OK, NetSec alert surge)
 * - T2_B12_02: All-offline card states when host and services are completely unreachable
 * - T2_B12_03: Extreme latency display (>10,000ms satellite/cellular connection)
 * - T2_B12_04: Rapid concurrent dashboard state refreshes across parallel coroutines
 * - T2_B12_05: Cache invalidation and state update on reconnect
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B12_ExecutiveDashBoundaryTest : E2eTestHarness() {

    private fun aggregateDashboard(
        telemetry: com.example.data.api.TelemetryResponse?,
        fleet: com.example.data.api.FleetStatusResponse?,
        netsec: com.example.data.api.NetSecOverviewResponse?,
        activeHost: HostEntity?
    ): ExecutiveDashboardSummary {
        val peakGpu = telemetry?.gpus?.maxOfOrNull { it.tempC } ?: 0
        val sysCard = if (telemetry != null) {
            "Optimal • ${peakGpu}°C (CPU: ${telemetry.cpu.tempC.toInt()}°C, RAM: ${telemetry.memory.usedPct.toInt()}%)"
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
            "Connected via Tailscale (${activeHost.lastLatencyMs}ms)"
        } else {
            "Offline"
        }

        val overall = when {
            activeHost == null || !activeHost.isOnline -> "OFFLINE"
            peakGpu >= 85 || (netsec?.crowdsecBanCount ?: 0) > 5 -> "CRITICAL"
            runningCount < fleetTotal || peakGpu >= 70 || (netsec?.suricataAlertCount ?: 0) > 10 -> "WARNING"
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
    fun T2_B12_01_partial_subsystem_outage_mixed_health() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val fleet = apiService.getFleetStatus()

        // Inject NetSec alert surge payload (15 alerts -> triggers WARNING)
        val alertSurgePayload = """
        {
          "status": "healthy",
          "suricata_alert_count": 15,
          "crowdsec_ban_count": 2,
          "total_dropped_packets": 99999
        }
        """.trimIndent()
        dispatcher.setResponse("/api/netsec/overview", 200, alertSurgePayload)
        val netsec = apiService.getNetSecOverview()

        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 12L)
        val dashboard = aggregateDashboard(telemetry, fleet, netsec, host)

        assertEquals("WARNING", dashboard.overallStatus)
        assertTrue(dashboard.securitySiemCard.contains("15 Alerts"))
    }

    @Test
    fun T2_B12_02_all_offline_card_states() {
        val dashboard = aggregateDashboard(null, null, null, null)

        assertEquals("System Health Unavailable", dashboard.systemHealthCard)
        assertEquals("Fleet Offline", dashboard.fleetDaemonsCard)
        assertEquals("Security Telemetry Offline", dashboard.securitySiemCard)
        assertEquals("Offline", dashboard.connectedWorkstationsCard)
        assertEquals("OFFLINE", dashboard.overallStatus)
    }

    @Test
    fun T2_B12_03_extreme_latency_display() = runBlocking {
        val extremeLatencyMs = 12450L
        val host = HostEntity(
            name = "fml-satellite",
            address = "100.111.123.93",
            isOnline = true,
            lastLatencyMs = extremeLatencyMs
        )

        val telemetry = apiService.getTelemetry()
        val fleet = apiService.getFleetStatus()
        val netsec = apiService.getNetSecOverview()

        val dashboard = aggregateDashboard(telemetry, fleet, netsec, host)
        assertTrue(dashboard.connectedWorkstationsCard.contains("12450ms"))
    }

    @Test
    fun T2_B12_04_rapid_concurrent_state_refreshes() = runBlocking {
        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 10L)

        val deferreds = (1..20).map { i ->
            async(Dispatchers.IO) {
                val telemetry = apiService.getTelemetry()
                val fleet = apiService.getFleetStatus()
                val netsec = apiService.getNetSecOverview()
                aggregateDashboard(telemetry, fleet, netsec, host)
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(20, results.size)
        assertTrue(results.all { it.overallStatus in listOf("OPTIMAL", "WARNING") })
    }

    @Test
    fun T2_B12_05_cache_invalidation_on_reconnect() = runBlocking {
        val initialHost = HostEntity(
            id = 1,
            name = "fml",
            address = "100.111.123.93",
            isOnline = false,
            lastLatencyMs = -1L
        )
        hostRepository.insertHost(initialHost)

        val offlineDb = hostRepository.getHostById(1).first()
        val offlineDashboard = aggregateDashboard(null, null, null, offlineDb)
        assertEquals("OFFLINE", offlineDashboard.overallStatus)

        // Reconnect host
        val reconnectedHost = initialHost.copy(
            isOnline = true,
            lastLatencyMs = 8L,
            activeEndpoint = "http://100.111.123.93:8899"
        )
        hostRepository.updateHost(reconnectedHost)

        val liveHost = hostRepository.getHostById(1).first()
        val liveTelemetry = apiService.getTelemetry()
        val liveFleet = apiService.getFleetStatus()
        val liveNetsec = apiService.getNetSecOverview()

        val liveDashboard = aggregateDashboard(liveTelemetry, liveFleet, liveNetsec, liveHost)
        assertTrue(liveDashboard.overallStatus != "OFFLINE")
        assertTrue(liveDashboard.connectedWorkstationsCard.contains("8ms"))
    }
}
