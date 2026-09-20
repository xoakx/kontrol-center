package com.example.e2e.tier1_features

import com.example.e2e.harness.E2eTestHarness
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
 * Tier 1 Tests for Feature 7: Hardware & Power Tunables.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F07_01: CPU governor performance lock action dispatch via TuneD
 * - T1_F07_02: CPU governor status telemetry verification
 * - T1_F07_03: Audio pipeline re-anchoring recovery action dispatch
 * - T1_F07_04: SRE sweep diagnostic action dispatch and failure detection
 * - T1_F07_05: Git sync repository audit action dispatch
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F07_HardwareTunablesTest : E2eTestHarness() {

    @Test
    fun T1_F07_01_cpu_governor_toggle_perf_lock() = runBlocking {
        val payload = """{"action": "perf_lock"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains("throughput-performance") == true)
            assertTrue(body?.contains("20 cores") == true)
        }
    }

    @Test
    fun T1_F07_02_cpu_governor_read_from_telemetry() = runBlocking {
        val telemetry = apiService.getTelemetry()
        assertEquals("performance", telemetry.cpu.governor)
        assertEquals(20, telemetry.cpu.cores)
    }

    @Test
    fun T1_F07_03_audio_pipeline_reanchor_action() = runBlocking {
        val payload = """{"action": "audio_reanchor"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains("Audio pipeline re-anchored successfully") == true)
        }
    }

    @Test
    fun T1_F07_04_sre_sweep_diagnostic_dispatch() = runBlocking {
        val payload = """{"action": "sre_sweep"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains("SRE Scan Complete") == true)
        }
    }

    @Test
    fun T1_F07_05_git_sync_status_dispatch() = runBlocking {
        val payload = """{"action": "git_sync"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains("Git Audit") == true)
        }
    }
}
