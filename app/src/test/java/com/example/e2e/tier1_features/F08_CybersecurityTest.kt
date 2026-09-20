package com.example.e2e.tier1_features

import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1 Tests for Feature 8: Cybersecurity Telemetry (Suricata, Tetragon, FW).
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F08_01: Suricata 8 intrusion detection alerts stream parsing
 * - T1_F08_02: Suricata severity rating classification (Critical, Warning, Info)
 * - T1_F08_03: Tetragon eBPF TracingPolicy inventory and enforcement modes
 * - T1_F08_04: Tetragon runtime security violation payload extraction (KPROBE_ACTION_SIGKILL)
 * - T1_F08_05: Nftables firewall posture and dropped packet telemetry
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F08_CybersecurityTest : E2eTestHarness() {

    @Test
    fun T1_F08_01_suricata_8_alerts_deserialization() = runBlocking {
        val response = apiService.getSuricataAlerts(50)
        assertNotNull(response)
        assertEquals(3, response.alerts.size)

        val alert1 = response.alerts[0]
        assertEquals("203.0.113.195", alert1.srcIp)
        assertEquals("192.168.1.161", alert1.destIp)
        assertEquals(22, alert1.destPort)
        assertEquals("TCP", alert1.proto)
        assertEquals("ET EXPLOIT Remote Command Execution Attempt", alert1.alert.signature)
        assertEquals(1, alert1.alert.severity)
    }

    @Test
    fun T1_F08_02_suricata_severity_badge_mapping() = runBlocking {
        val response = apiService.getSuricataAlerts(50)
        val alerts = response.alerts

        // Verify severity levels present in dataset
        val sev1 = alerts.find { it.alert.severity == 1 }
        val sev2 = alerts.find { it.alert.severity == 2 }
        val sev3 = alerts.find { it.alert.severity == 3 }

        assertNotNull(sev1)
        assertNotNull(sev2)
        assertNotNull(sev3)

        assertEquals("ET EXPLOIT Remote Command Execution Attempt", sev1?.alert?.signature)
        assertEquals("ET SCAN Nmap SYN Scan", sev2?.alert?.signature)
        assertEquals("ET INFO DNS Query for Known Domain", sev3?.alert?.signature)
    }

    @Test
    fun T1_F08_03_tetragon_ebpf_policy_inventory() = runBlocking {
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/netsec/tetragon/status")
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains("anti-reverse-shell") == true)
            assertTrue(body?.contains("scratch-exec") == true)
            assertTrue(body?.contains(""""mode": "enforce"""") == true)
        }
    }

    @Test
    fun T1_F08_04_tetragon_runtime_security_violations() = runBlocking {
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/netsec/tetragon/status")
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains("/bin/bash") == true)
            assertTrue(body?.contains("KPROBE_ACTION_SIGKILL") == true)
            assertTrue(body?.contains("1639879") == true)
        }
    }

    @Test
    fun T1_F08_05_nftables_firewall_posture_monitoring() = runBlocking {
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/netsec/firewall/status")
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains(""""tableCount": 11""") == true || body?.contains(""""tableCount":11""") == true)
            assertTrue(body?.contains("14820") == true)
            assertTrue(body?.contains("ts-input") == true)
        }
    }
}
