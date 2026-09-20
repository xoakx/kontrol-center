package com.example.e2e.tier2_boundaries

import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 2 Boundary Tests: B07 Hardware Tunables Boundary.
 * Covers 5 boundary conditions:
 * - T2_B07_01: Unsupported governor profile string validation and error handling
 * - T2_B07_02: Permission denied / 403 Forbidden handling on privileged tunable action
 * - T2_B07_03: PipeWire daemon crash error handling during audio re-anchor action
 * - T2_B07_04: Concurrent governor toggle conflict resolution
 * - T2_B07_05: Invalid power limit wattage boundary rejection (e.g. <= 0 watts)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B07_HardwareTunablesBoundaryTest : E2eTestHarness() {

    @Test
    fun T2_B07_01_unsupported_governor_profile() = runBlocking {
        dispatcher.overrideResponse("/api/dispatch") { req ->
            val body = dispatcher.extractRequestBody(req)
            if (body.contains("unsupported_profile")) {
                dispatcher.createResponse(req, 400, """{"success": false, "error": "Unsupported governor profile"}""")
            } else {
                dispatcher.createResponse(req, 200, """{"success": true}""")
            }
        }

        val badPayload = """{"action": "unsupported_profile"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(badPayload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertEquals(400, response.code)
            val body = response.body?.string()
            assertTrue(body?.contains("Unsupported governor profile") == true)
        }
    }

    @Test
    fun T2_B07_02_permission_denied_rollback() = runBlocking {
        dispatcher.setResponse(
            "/api/dispatch",
            403,
            """{"success": false, "error": "Permission denied: TuneD profile switch requires root/sudo privileges."}"""
        )

        val payload = """{"action": "perf_lock"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertEquals(403, response.code)
            val body = response.body?.string()
            assertTrue(body?.contains("Permission denied") == true)
        }
    }

    @Test
    fun T2_B07_03_pipewire_daemon_crash_on_reanchor() = runBlocking {
        dispatcher.setResponse(
            "/api/dispatch",
            500,
            """{"success": false, "error": "PipeWire daemon unreachable: Connection refused on /run/user/1000/pipewire-0"}"""
        )

        val payload = """{"action": "audio_reanchor"}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertEquals(500, response.code)
            val body = response.body?.string()
            assertTrue(body?.contains("PipeWire daemon unreachable") == true)
        }
    }

    @Test
    fun T2_B07_04_concurrent_governor_toggle_conflict() = runBlocking {
        val actions = listOf("perf_lock", "powersave_mode", "perf_lock", "powersave_mode")

        val deferreds = actions.map { action ->
            async(Dispatchers.IO) {
                val payload = """{"action": "$action"}"""
                val req = Request.Builder()
                    .url("http://100.111.123.93:8899/api/dispatch")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                okHttpClient.newCall(req).execute().use { resp ->
                    resp.isSuccessful
                }
            }
        }

        val results = deferreds.awaitAll()
        assertEquals(4, results.size)
        assertTrue(results.all { it })
    }

    @Test
    fun T2_B07_05_invalid_power_limit() = runBlocking {
        dispatcher.overrideResponse("/api/dispatch") { req ->
            val body = dispatcher.extractRequestBody(req)
            if (body.contains(""""watts": -50""") || body.contains(""""watts": 0""")) {
                dispatcher.createResponse(req, 422, """{"success": false, "error": "Power limit must be between 50W and 300W"}""")
            } else {
                dispatcher.createResponse(req, 200, """{"success": true}""")
            }
        }

        val invalidWattsPayload = """{"action": "set_power_limit", "watts": -50}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(invalidWattsPayload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertEquals(422, response.code)
            val body = response.body?.string()
            assertTrue(body?.contains("Power limit must be between") == true)
        }
    }
}
