package com.example.data

import com.example.data.api.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArcadeDtoParsingTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun testLoginDtoParsing() {
        val reqJson = """{"token":"test_secret_123"}"""
        val req = moshi.adapter(LoginRequest::class.java).fromJson(reqJson)
        assertNotNull(req)
        assertEquals("test_secret_123", req!!.token)

        val respJson = """{"access_token":"jwt_header.payload.signature"}"""
        val resp = moshi.adapter(LoginResponse::class.java).fromJson(respJson)
        assertNotNull(resp)
        assertEquals("jwt_header.payload.signature", resp!!.accessToken)
    }

    @Test
    fun testTelemetryResponseParsingWithDualGpus() {
        val json = """
        {
          "cpu": {
            "load_1m": "2.61",
            "load_5m": "2.25",
            "load_15m": "1.87",
            "governor": "performance",
            "temp_c": 55.0,
            "cores": 20
          },
          "memory": {
            "total_mb": 63665,
            "used_mb": 36539,
            "used_pct": 57.4
          },
          "storage": {
            "total_gb": 931.2,
            "used_gb": 732.3,
            "free_gb": 196.4,
            "used_pct": 78.6,
            "mount": "/"
          },
          "npu": {
            "present": true,
            "model": "Intel AI Boost NPU 4 (Arrow Lake)",
            "device": "/dev/accel/accel0",
            "service": "gemini-npu-embeddings.service"
          },
          "gpus": [
            {
              "index": "0",
              "name": "NVIDIA GeForce RTX 5060 Ti",
              "temp_c": 39,
              "util_pct": 5,
              "mem_used_mb": 6429,
              "mem_total_mb": 16311,
              "power_w": 13.79,
              "fan_speed_pct": 0,
              "clock_mhz": 600
            },
            {
              "index": "1",
              "name": "NVIDIA GeForce RTX 5060 Ti",
              "temp_c": 27,
              "util_pct": 0,
              "mem_used_mb": 10073,
              "mem_total_mb": 16311,
              "power_w": 3.84,
              "fan_speed_pct": 0,
              "clock_mhz": 180
            }
          ],
          "network": {
            "congestion_control": "bbr",
            "qdisc": "fq"
          },
          "uptime_hours": 27.5
        }
        """.trimIndent()

        val adapter = moshi.adapter(TelemetryResponse::class.java)
        val telem = adapter.fromJson(json)

        assertNotNull(telem)
        assertEquals(20, telem!!.cpu.cores)
        assertEquals("performance", telem.cpu.governor)
        assertEquals(55.0, telem.cpu.tempC, 0.01)
        assertTrue(telem.npu.present)
        assertEquals(2, telem.gpus.size)
        assertEquals("0", telem.gpus[0].index)
        assertEquals(13.79, telem.gpus[0].powerW, 0.01)
        assertEquals(27.5, telem.uptimeHours, 0.01)
    }

    @Test
    fun testFleetStatusResponseWithRunningAndStoppedDaemons() {
        val json = """
        {
          "agents": [
            {
              "id": "gemini-scribe",
              "name": "Gemini Scribe Daemon",
              "service": "gemini-scribe.service",
              "category": "Documentation & Telemetry",
              "description": "Monitors sessions",
              "icon": "scribe",
              "status": {
                "active": true,
                "state": "running",
                "pid": 2185352,
                "memory_mb": 269.7
              },
              "is_dynamic": false
            },
            {
              "id": "gemini-voice",
              "name": "Gemini Live Copilot",
              "service": "gemini-voice.service",
              "category": "Audio & Speech AI",
              "description": "Voice copilot",
              "icon": "audio",
              "status": {
                "active": false,
                "state": "stopped",
                "pid": null,
                "memory_mb": null
              },
              "is_dynamic": false
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(FleetStatusResponse::class.java)
        val fleet = adapter.fromJson(json)

        assertNotNull(fleet)
        assertEquals(2, fleet!!.agents.size)
        assertEquals(true, fleet.agents[0].status.active)
        assertEquals(2185352L, fleet.agents[0].status.pid)
        assertEquals(false, fleet.agents[1].status.active)
        assertNull(fleet.agents[1].status.pid)
        assertNull(fleet.agents[1].status.memoryMb)
    }

    @Test
    fun testAgentControlDtoParsing() {
        val reqJson = """{"service":"gemini-scribe.service","action":"restart"}"""
        val req = moshi.adapter(AgentControlRequest::class.java).fromJson(reqJson)
        assertNotNull(req)
        assertEquals("gemini-scribe.service", req!!.service)
        assertEquals("restart", req.action)

        val respJson = """
        {
          "success": true,
          "service": "gemini-scribe.service",
          "action": "restart",
          "status": {
            "active": true,
            "state": "running"
          }
        }
        """.trimIndent()
        val resp = moshi.adapter(AgentControlResponse::class.java).fromJson(respJson)
        assertNotNull(resp)
        assertTrue(resp!!.success)
        assertEquals(true, resp.status?.active)
        assertEquals("running", resp.status?.state)
    }

    @Test
    fun testSmartHomeResponseParsing() {
        val json = """
        {
          "sensors": {
            "msr2_living_room": {
              "name": "Apollo MSR-2 (Living Room)",
              "type": "mmwave_multisensor",
              "presence": false,
              "target_distance_m": 0.0,
              "movement_energy": 63,
              "still_energy": 19,
              "illuminance_lux": 5.7,
              "temperature_c": 34.3,
              "co2_ppm": 0,
              "firmware": "2026.8.2"
            }
          },
          "voice_satellites": {
            "xvf3800_living_room": {
              "name": "XMOS XVF3800 DSP Array",
              "location": "Living Room (Main Area)",
              "state": "idle",
              "beam_angle_deg": 142
            }
          },
          "zigbee_perimeter": {
            "sonoff_front_door": {
              "name": "Sonoff Contact - Front Door",
              "model": "SNZB-04P",
              "state": "open",
              "battery_pct": 100,
              "lqi": 112
            }
          },
          "air_purifier": {
            "levoit_core": {
              "name": "Levoit Smart Air Purifier",
              "power": "offline",
              "fan_speed": 0,
              "pm25_aqi": 0
            }
          }
        }
        """.trimIndent()

        val adapter = moshi.adapter(SmartHomeResponse::class.java)
        val sh = adapter.fromJson(json)

        assertNotNull(sh)
        assertEquals(1, sh!!.sensors.size)
        val radar = sh.sensors["msr2_living_room"]
        assertNotNull(radar)
        assertEquals(false, radar!!.presence)
        assertEquals(34.3, radar.temperatureC, 0.01)
        assertEquals("open", sh.zigbeePerimeter["sonoff_front_door"]?.state)
    }

    @Test
    fun testRfcResponseParsing() {
        val json = """
        {
          "rfcs": [
            {
              "id": "RFC-0B4535",
              "source": "stress-tester",
              "title": "Automated Vector Index Compaction",
              "category": "OBSERVABILITY",
              "description": "Stress testing RFC submission pipeline",
              "proposed_steps": ["Step A", "Step B"],
              "risk_level": "LOW",
              "status": "APPROVED",
              "task_id": "task_rfc-0b4535_c631",
              "created_at": "2026-09-19T23:04:34Z",
              "updated_at": "2026-09-19T23:04:34Z",
              "resolved_by": "Arcade_Operator"
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(RfcsResponse::class.java)
        val rfcs = adapter.fromJson(json)

        assertNotNull(rfcs)
        assertEquals(1, rfcs!!.rfcs.size)
        assertEquals("RFC-0B4535", rfcs.rfcs[0].id)
        assertEquals("APPROVED", rfcs.rfcs[0].status)
        assertEquals(2, rfcs.rfcs[0].proposedSteps.size)
    }

    @Test
    fun testRfcVoteDtoParsing() {
        val voteReq = """{"decision":"approve"}"""
        val req = moshi.adapter(RfcVoteRequest::class.java).fromJson(voteReq)
        assertNotNull(req)
        assertEquals("approve", req!!.decision)

        val voteResp = """{"success":true,"status":"APPROVED","task_id":"task_123"}"""
        val resp = moshi.adapter(RfcVoteResponse::class.java).fromJson(voteResp)
        assertNotNull(resp)
        assertTrue(resp!!.success)
        assertEquals("APPROVED", resp.status)
        assertEquals("task_123", resp.taskId)
    }

    @Test
    fun testNetSecOverviewResponseParsing() {
        val json = """
        {
          "status": "healthy",
          "suricata_active": true,
          "suricata_alert_count": 5,
          "crowdsec_active": true,
          "crowdsec_ban_count": 2,
          "tetragon_health": "operational",
          "total_dropped_packets": 1420
        }
        """.trimIndent()

        val adapter = moshi.adapter(NetSecOverviewResponse::class.java)
        val overview = adapter.fromJson(json)
        assertNotNull(overview)
        assertTrue(overview!!.suricataActive)
        assertEquals(5, overview.suricataAlertCount)
        assertEquals(2, overview.crowdsecBanCount)
        assertEquals(1420L, overview.totalDroppedPackets)
    }

    @Test
    fun testNetSecSuricataAlertsAndCrowdSecUnbanParsing() {
        val alertJson = """
        {
          "status": "success",
          "count": 1,
          "alerts": [
            {
              "timestamp": "2026-09-19T20:00:00Z",
              "src_ip": "198.51.100.23",
              "dest_ip": "192.168.1.161",
              "dest_port": 22,
              "alert": {
                "signature": "ET SCAN Potential SSH Scan",
                "category": "Attempted Information Leak",
                "severity": 1
              }
            }
          ]
        }
        """.trimIndent()

        val alertAdapter = moshi.adapter(SuricataAlertsResponse::class.java)
        val alertResp = alertAdapter.fromJson(alertJson)
        assertNotNull(alertResp)
        assertEquals(1, alertResp!!.count)
        assertEquals(1, alertResp.alerts[0].alert.severity)

        val unbanJson = """
        {
          "success": true,
          "action": "delete",
          "ip": "198.51.100.23",
          "output": "decision for ip '198.51.100.23' deleted"
        }
        """.trimIndent()

        val unbanAdapter = moshi.adapter(UnbanResponse::class.java)
        val unbanResp = unbanAdapter.fromJson(unbanJson)
        assertNotNull(unbanResp)
        assertTrue(unbanResp!!.success)
        assertEquals("198.51.100.23", unbanResp.ip)
    }

    @Test
    fun testCrowdSecDecisionsResponseParsing() {
        val json = """
        {
          "status": "success",
          "action": "list",
          "active_decisions": [
            {
              "id": 101,
              "origin": "cscli",
              "type": "ban",
              "scope": "Ip",
              "value": "192.0.2.1",
              "duration": "4h",
              "until": "2026-09-20T04:00:00Z",
              "scenario": "crowdsecurity/ssh-bf"
            }
          ],
          "decision_count": 1,
          "bouncers": [
            {
              "name": "firewall-bouncer",
              "type": "crowdsec-firewall-bouncer",
              "valid": true
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(CrowdSecDecisionsResponse::class.java)
        val resp = adapter.fromJson(json)
        assertNotNull(resp)
        assertEquals(1, resp!!.decisionCount)
        assertEquals(1, resp.activeDecisions.size)
        assertEquals("192.0.2.1", resp.activeDecisions[0].value)
        assertEquals("crowdsecurity/ssh-bf", resp.activeDecisions[0].scenario)
    }

    @Test
    fun testWebSocketEventBusParsing() {
        val json = """
        {
          "id": 13203,
          "topic": "rfc.status",
          "source": "arbitrator",
          "payload": {"status": "APPROVED"},
          "timestamp": "2026-09-20T00:13:23.319643Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(ArcadeEventBusMessage::class.java)
        val msg = adapter.fromJson(json)
        assertNotNull(msg)
        assertEquals(13203L, msg!!.id)
        assertEquals("rfc.status", msg.topic)
        assertEquals("arbitrator", msg.source)
        assertEquals("APPROVED", msg.payload["status"])
    }
}
