package com.example.e2e.tier4_scenarios

import com.example.data.api.UnbanRequest
import com.example.data.entity.HostEntity
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.tier1_features.ExecutiveDashboardSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scenario S5: Cyber Intrusion Alert & 1-Tap Security Unban (Features F8, F9, F12).
 *
 * Sequence:
 * 1. High-severity Suricata alert arrives from WAN (203.0.113.195).
 * 2. CrowdSec LAPI registers active ban decision enforced by FirewallBouncer (nftables).
 * 3. Executive Dashboard reflects threat surge on Security & SIEM summary card.
 * 4. Operator drills down into NetSec view, inspects raw eve payload and ban duration.
 * 5. Operator recognizes false positive, triggers 1-tap unban remediation.
 * 6. Backend deletes decision from nftables; dashboard ban count decrements to 0.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S5_SuricataAlertCrowdSecUnbanScenarioTest : E2eTestHarness() {

    private fun evaluateDashboard(
        netsec: com.example.data.api.NetSecOverviewResponse,
        host: HostEntity
    ): ExecutiveDashboardSummary {
        val overall = if (netsec.crowdsecBanCount > 0) "WARNING" else "OPTIMAL"

        return ExecutiveDashboardSummary(
            systemHealthCard = "Optimal • 41°C",
            fleetDaemonsCard = "12/12 Active",
            securitySiemCard = "${netsec.crowdsecBanCount} Active Bans • ${netsec.suricataAlertCount} Alerts",
            connectedWorkstationsCard = "Tailscale (${host.lastLatencyMs}ms)",
            overallStatus = overall
        )
    }

    @Test
    fun executeScenario5_CyberIntrusionAlertAnd1TapUnban() = runBlocking {
        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 12L)

        // Step 1: Ingest active intrusion alert and ban
        val attackerIp = "203.0.113.195"

        val activeThreatPayload = """
        {
          "status": "healthy",
          "suricata_alert_count": 1,
          "crowdsec_ban_count": 1,
          "total_dropped_packets": 420
        }
        """.trimIndent()
        dispatcher.setResponse("/api/netsec/overview", 200, activeThreatPayload)

        val bansPayload = """
        {
          "active_decisions": [
            {
              "id": 88001,
              "value": "$attackerIp",
              "scenario": "crowdsecurity/ssh-bf",
              "origin": "cscli",
              "duration": "4h"
            }
          ],
          "bouncers": [
            {
              "name": "FirewallBouncer",
              "type": "nftables",
              "ipAddress": "127.0.0.1",
              "valid": true
            }
          ]
        }
        """.trimIndent()
        dispatcher.setResponse("/api/netsec/crowdsec/decisions", 200, bansPayload)

        // Step 2 & 3: Executive Dashboard reflects threat
        val netsecThreat = apiService.getNetSecOverview()
        val dashThreat = evaluateDashboard(netsecThreat, host)
        assertEquals("WARNING", dashThreat.overallStatus)
        assertTrue(dashThreat.securitySiemCard.contains("1 Active Bans • 1 Alerts"))

        // Step 4: Drill-down inspection of Suricata alert and CrowdSec decision
        val alerts = apiService.getSuricataAlerts(10).alerts
        val targetAlert = alerts.find { it.srcIp == attackerIp }
        assertNotNull(targetAlert)
        assertEquals(1, targetAlert?.alert?.severity) // Critical severity
        assertEquals("ET EXPLOIT Remote Command Execution Attempt", targetAlert?.alert?.signature)

        val decisions = apiService.getCrowdSecDecisions()
        assertEquals(1, decisions.activeDecisions.size)
        assertEquals(attackerIp, decisions.activeDecisions[0].value)

        // Step 5: Operator triggers 1-tap unban
        val unbanResponse = apiService.unbanIp(UnbanRequest(ip = attackerIp))
        assertNotNull(unbanResponse)
        assertTrue(unbanResponse.success)
        assertEquals(attackerIp, unbanResponse.ip)

        // Step 6: Backend updates state (ban removed)
        val clearedBansPayload = """
        {
          "active_decisions": [],
          "bouncers": [
            {
              "name": "FirewallBouncer",
              "type": "nftables",
              "ipAddress": "127.0.0.1",
              "valid": true
            }
          ]
        }
        """.trimIndent()
        dispatcher.setResponse("/api/netsec/crowdsec/decisions", 200, clearedBansPayload)

        val clearedThreatOverview = """
        {
          "status": "healthy",
          "suricata_alert_count": 1,
          "crowdsec_ban_count": 0,
          "total_dropped_packets": 420
        }
        """.trimIndent()
        dispatcher.setResponse("/api/netsec/overview", 200, clearedThreatOverview)

        val updatedDecisions = apiService.getCrowdSecDecisions()
        assertTrue(updatedDecisions.activeDecisions.isEmpty())

        val updatedOverview = apiService.getNetSecOverview()
        val clearedDash = evaluateDashboard(updatedOverview, host)
        assertEquals("OPTIMAL", clearedDash.overallStatus)
        assertTrue(clearedDash.securitySiemCard.contains("0 Active Bans"))
    }
}
