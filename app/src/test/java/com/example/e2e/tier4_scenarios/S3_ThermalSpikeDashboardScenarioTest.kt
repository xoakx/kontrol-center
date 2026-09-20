package com.example.e2e.tier4_scenarios

import com.example.data.entity.HostEntity
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import com.example.e2e.tier1_features.ExecutiveDashboardSummary
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
 * Scenario S3: Extreme Load & Silicon Thermal Spike (Features F3, F5, F6, F12).
 *
 * Sequence:
 * 1. Initial state: System runs at cool idle (Dual GPUs @ 41°C/27°C, CPU @ 42°C).
 * 2. High-compute ML inference triggers dual GPU & 20-core CPU thermal surge.
 * 3. Telemetry stream receives spike payload (GPU0 @ 89°C, CPU @ 86°C).
 * 4. Executive Dashboard recalculates status in <2 seconds, switching badge to CRITICAL RED.
 * 5. Smart Home sensor correlates ambient environment telemetry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S3_ThermalSpikeDashboardScenarioTest : E2eTestHarness() {

    private fun evaluateDashboard(
        telemetry: com.example.data.api.TelemetryResponse,
        fleet: com.example.data.api.FleetStatusResponse,
        netsec: com.example.data.api.NetSecOverviewResponse,
        host: HostEntity
    ): ExecutiveDashboardSummary {
        val peakGpu = telemetry.gpus.maxOfOrNull { it.tempC } ?: 0
        val cpuTemp = telemetry.cpu.tempC

        val overall = when {
            peakGpu >= 85 || cpuTemp >= 85.0 -> "CRITICAL"
            peakGpu >= 70 || cpuTemp >= 75.0 -> "WARNING"
            else -> "OPTIMAL"
        }

        return ExecutiveDashboardSummary(
            systemHealthCard = "Silicon: ${peakGpu}°C (CPU: ${cpuTemp.toInt()}°C)",
            fleetDaemonsCard = "${fleet.agents.size}/${fleet.agents.size} Active",
            securitySiemCard = "${netsec.crowdsecBanCount} Bans",
            connectedWorkstationsCard = "Tailscale",
            overallStatus = overall
        )
    }

    @Test
    fun executeScenario3_ExtremeLoadAndThermalSpikeDashboard() = runBlocking {
        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 12L)
        val fleet = apiService.getFleetStatus()
        val netsec = apiService.getNetSecOverview()

        // 1. Initial Baseline Check
        val idleTelemetry = apiService.getTelemetry()
        val idleDashboard = evaluateDashboard(idleTelemetry, fleet, netsec, host)
        assertEquals("OPTIMAL", idleDashboard.overallStatus)
        assertTrue(idleTelemetry.gpus[0].tempC < 50)

        // 2. Inject Extreme Thermal Spike Payload
        dispatcher.setResponse("/api/telemetry", 200, MockTelemetryPayloads.thermalSpikeTelemetryJson())

        // Measure evaluation duration to confirm <2000ms SLA
        var spikeTelemetry: com.example.data.api.TelemetryResponse? = null
        var spikeDashboard: ExecutiveDashboardSummary? = null

        val evaluationTimeMs = measureTimeMillis {
            spikeTelemetry = apiService.getTelemetry()
            spikeDashboard = evaluateDashboard(spikeTelemetry!!, fleet, netsec, host)
        }

        // 3. Confirm transition to CRITICAL within SLA
        assertTrue("Dashboard evaluation must complete under 2000ms SLA", evaluationTimeMs < 2000)
        assertNotNull(spikeTelemetry)
        assertNotNull(spikeDashboard)
        assertEquals("CRITICAL", spikeDashboard?.overallStatus)

        // 4. Verify GPU & CPU specifics
        val peakGpuTemp = spikeTelemetry!!.gpus.maxOf { it.tempC }
        val cpuTemp = spikeTelemetry!!.cpu.tempC
        assertEquals(89, peakGpuTemp)
        assertEquals(86.0, cpuTemp, 0.1)

        // 5. Verify Smart Home ambient reading
        val smarthome = apiService.getSmartHome()
        val apollo = smarthome.sensors["apollo_msr2"]
        assertNotNull(apollo)
        assertEquals(640, apollo?.co2Ppm)
    }
}
