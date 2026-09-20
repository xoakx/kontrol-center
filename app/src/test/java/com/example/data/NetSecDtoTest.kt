package com.example.data

import com.example.data.api.CrowdSecDecisionItem
import com.example.data.api.CrowdSecDecisionsResponse
import com.example.data.api.FirewallStatusResponse
import com.example.data.api.NetSecOverviewResponse
import com.example.data.api.SuricataAlertItem
import com.example.data.api.SuricataAlertsResponse
import com.example.data.api.TetragonStatusResponse
import com.example.data.api.UnbanRequest
import com.example.data.api.UnbanResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetSecDtoTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun testNetSecOverview_fullPayload() {
        val json = """
        {
          "status": "healthy",
          "suricata_active": true,
          "suricata_alert_count": 5,
          "crowdsec_active": true,
          "crowdsec_ban_count": 2,
          "tetragon_health": "healthy",
          "total_dropped_packets": 14820
        }
        """.trimIndent()

        val overview = moshi.adapter(NetSecOverviewResponse::class.java).fromJson(json)
        assertNotNull(overview)
        assertEquals("healthy", overview!!.status)
        assertTrue(overview.suricataActive)
        assertEquals(5, overview.suricataAlertCount)
        assertTrue(overview.crowdsecActive)
        assertEquals(2, overview.crowdsecBanCount)
        assertEquals("healthy", overview.tetragonHealth)
        assertEquals(14820L, overview.totalDroppedPackets)
    }

    @Test
    fun testNetSecOverview_defaultFallback() {
        val json = "{}"
        val overview = moshi.adapter(NetSecOverviewResponse::class.java).fromJson(json)
        assertNotNull(overview)
        assertEquals("healthy", overview!!.status)
        assertTrue(overview.suricataActive)
        assertEquals(0, overview.suricataAlertCount)
        assertTrue(overview.crowdsecActive)
        assertEquals(0, overview.crowdsecBanCount)
        assertEquals("healthy", overview.tetragonHealth)
        assertEquals(0L, overview.totalDroppedPackets)
    }

    @Test
    fun testSuricataAlerts_complexEvePayload() {
        val json = """
        {
          "status": "success",
          "count": 3,
          "alerts": [
            {
              "timestamp": "2026-09-20T00:15:32.123456Z",
              "event_type": "alert",
              "src_ip": "203.0.113.195",
              "src_port": 54321,
              "dest_ip": "192.168.1.161",
              "dest_port": 22,
              "proto": "TCP",
              "alert": {
                "action": "allowed",
                "gid": 1,
                "signature_id": 2010935,
                "rev": 3,
                "signature": "ET EXPLOIT Remote Command Execution Attempt",
                "category": "Attempted Administrator Privilege Gain",
                "severity": 1
              }
            },
            {
              "timestamp": "2026-09-20T00:16:01.654321Z",
              "event_type": "alert",
              "src_ip": "198.51.100.23",
              "src_port": 49152,
              "dest_ip": "192.168.1.161",
              "dest_port": 8899,
              "proto": "TCP",
              "alert": {
                "action": "allowed",
                "gid": 1,
                "signature_id": 2001219,
                "rev": 2,
                "signature": "ET SCAN Nmap SYN Scan",
                "category": "Detection of a Network Scan",
                "severity": 2
              }
            },
            {
              "timestamp": "2026-09-20T00:17:45.987654Z",
              "event_type": "alert",
              "src_ip": "192.168.1.105",
              "src_port": 5353,
              "dest_ip": "224.0.0.251",
              "dest_port": 5353,
              "proto": "UDP",
              "alert": {
                "action": "allowed",
                "gid": 1,
                "signature_id": 2024101,
                "rev": 1,
                "signature": "ET INFO DNS Query for Known Domain",
                "category": "Potentially Bad Traffic",
                "severity": 3
              }
            }
          ]
        }
        """.trimIndent()

        val response = moshi.adapter(SuricataAlertsResponse::class.java).fromJson(json)
        assertNotNull(response)
        assertEquals(3, response!!.alerts.size)

        val alert1 = response.alerts[0]
        assertEquals("203.0.113.195", alert1.srcIp)
        assertEquals(22, alert1.destPort)
        assertEquals("TCP", alert1.proto)
        assertEquals("ET EXPLOIT Remote Command Execution Attempt", alert1.alert.signature)
        assertEquals(1, alert1.alert.severity)
    }

    @Test
    fun testSuricataAlertItem_nullableOptionalFields() {
        val json = """
        {
          "timestamp": "2026-09-20T00:18:10.000000Z",
          "event_type": "alert",
          "src_ip": "10.0.0.1",
          "dest_ip": "10.0.0.2",
          "proto": "ICMP",
          "alert": {
            "signature": "ICMP Echo Request",
            "severity": 3
          }
        }
        """.trimIndent()

        val alert = moshi.adapter(SuricataAlertItem::class.java).fromJson(json)
        assertNotNull(alert)
        assertNull(alert!!.destPort)
        assertNull(alert.srcPort)
        assertEquals("", alert.alert.category)
        assertEquals("ICMP", alert.proto)
    }

    @Test
    fun testCrowdSecDecisions_activeDecisionsAndBouncers() {
        val json = """
        {
          "status": "success",
          "action": "list",
          "active_decisions": [
            {
              "id": 14951,
              "origin": "CAPI",
              "type": "ban",
              "scope": "Ip",
              "value": "209.99.190.113",
              "duration": "3h 45m",
              "until": "2026-09-20T04:00:00Z",
              "scenario": "ssh:bruteforce"
            },
            {
              "id": 14952,
              "origin": "cscli",
              "type": "ban",
              "scope": "Ip",
              "value": "198.51.100.4",
              "duration": "11h 20m",
              "until": "2026-09-20T11:30:00Z",
              "scenario": "http:crawl"
            }
          ],
          "decision_count": 2,
          "bouncers": [
            {
              "name": "FirewallBouncer",
              "type": "nftables",
              "valid": true
            },
            {
              "name": "cloudflare-bouncer",
              "type": "api",
              "valid": true
            }
          ]
        }
        """.trimIndent()

        val resp = moshi.adapter(CrowdSecDecisionsResponse::class.java).fromJson(json)
        assertNotNull(resp)
        assertEquals(2, resp!!.activeDecisions.size)
        assertEquals(14951L, resp.activeDecisions[0].id)
        assertEquals("ssh:bruteforce", resp.activeDecisions[0].scenario)
        assertEquals(2, resp.bouncers.size)
        assertEquals("FirewallBouncer", resp.bouncers[0].name)
        assertTrue(resp.bouncers[0].valid)
    }

    @Test
    fun testCrowdSecDecisions_emptyList() {
        val json = """
        {
          "status": "success",
          "action": "list",
          "active_decisions": [],
          "decision_count": 0,
          "bouncers": []
        }
        """.trimIndent()

        val resp = moshi.adapter(CrowdSecDecisionsResponse::class.java).fromJson(json)
        assertNotNull(resp)
        assertTrue(resp!!.activeDecisions.isEmpty())
        assertEquals(0, resp.decisionCount)
        assertTrue(resp.bouncers.isEmpty())
    }

    @Test
    fun testCrowdSecDecisionItem_cidrSubnet() {
        val json = """
        {
          "id": 14999,
          "origin": "cscli",
          "type": "ban",
          "scope": "Range",
          "value": "198.51.100.0/24",
          "duration": "24h",
          "until": "2026-09-21T00:00:00Z",
          "scenario": "ddos:flood"
        }
        """.trimIndent()

        val item = moshi.adapter(CrowdSecDecisionItem::class.java).fromJson(json)
        assertNotNull(item)
        assertEquals("Range", item!!.scope)
        assertEquals("198.51.100.0/24", item.value)
        assertEquals("ddos:flood", item.scenario)
    }

    @Test
    fun testUnbanRequest_serialization() {
        val req = UnbanRequest(ip = "209.99.190.113")
        val json = moshi.adapter(UnbanRequest::class.java).toJson(req)
        assertTrue(json.contains(""""ip":"209.99.190.113""""))
    }

    @Test
    fun testUnbanResponse_success() {
        val json = """
        {
          "success": true,
          "action": "delete",
          "ip": "209.99.190.113",
          "output": "Decision deleted",
          "message": "Decision for 209.99.190.113 deleted successfully"
        }
        """.trimIndent()

        val resp = moshi.adapter(UnbanResponse::class.java).fromJson(json)
        assertNotNull(resp)
        assertTrue(resp!!.success)
        assertEquals("209.99.190.113", resp.ip)
        assertEquals("delete", resp.action)
        assertNotNull(resp.message)
        assertEquals("Decision for 209.99.190.113 deleted successfully", resp.message)
    }

    @Test
    fun testUnbanResponse_failure() {
        val json = """
        {
          "success": false,
          "action": "delete",
          "ip": "209.99.190.113",
          "output": "failed",
          "message": "No decision found"
        }
        """.trimIndent()

        val resp = moshi.adapter(UnbanResponse::class.java).fromJson(json)
        assertNotNull(resp)
        assertFalse(resp!!.success)
        assertEquals("failed", resp.output)
        assertEquals("No decision found", resp.message)
    }
}
