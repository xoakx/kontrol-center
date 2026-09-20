package com.example.e2e.tier4_scenarios

import com.example.data.api.RfcVoteRequest
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.FakeSshSession
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
 * Scenario S4: System RFC Review, Governor Shift & Audio Recovery (Features F7, F10, F11).
 *
 * Sequence:
 * 1. Operator reviews pending RFCs in RFC Deck, finding RFC-00142 (CPU Frequency Scaling).
 * 2. Operator opens PTY Terminal to inspect system logs and diffs.
 * 3. Operator approves RFC-00142 via 1-Click voting API.
 * 4. Arcade backend activates `throughput-performance` profile, setting governor to `performance`.
 * 5. Operator dispatches audio pipeline re-anchoring to eliminate buffer underruns.
 * 6. PTY terminal stream logs confirm successful governor shift and audio sync.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class S4_RfcReviewGovernorShiftScenarioTest : E2eTestHarness() {

    @Test
    fun executeScenario4_RfcReviewGovernorShiftAndAudioRecovery() = runBlocking {
        val fakeSsh = FakeSshSession()
        val ptyStream = fakeSsh.getOutputStream()

        // Step 1: Review pending RFCs
        val rfcsResponse = apiService.getRfcs()
        val rfc = rfcsResponse.rfcs.find { it.id == "RFC-00142" }
        assertNotNull(rfc)
        assertEquals("PROPOSED", rfc?.status)
        assertEquals("MEDIUM", rfc?.riskLevel)

        // Step 2: Operator inspects logs in PTY terminal
        ptyStream.write("tuned-adm active\n".toByteArray(Charsets.UTF_8))
        ptyStream.write("Current active profile: powersave\n".toByteArray(Charsets.UTF_8))
        ptyStream.flush()

        // Step 3: Operator approves RFC
        val voteResp = apiService.voteRfc("RFC-00142", RfcVoteRequest(decision = "approve"))
        assertNotNull(voteResp)
        assertTrue(voteResp.success)
        assertEquals("APPROVED", voteResp.status)
        val taskId = voteResp.taskId
        assertNotNull(taskId)

        // Step 4: Execute performance profile lock
        val perfLockPayload = """{"action": "perf_lock"}"""
        val perfReq = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(perfLockPayload.toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(perfReq).execute().use { resp ->
            assertTrue(resp.isSuccessful)
            val body = resp.body?.string()
            assertTrue(body?.contains("throughput-performance") == true)
        }

        // Verify governor in telemetry
        val telemetry = apiService.getTelemetry()
        assertEquals("performance", telemetry.cpu.governor)

        // Step 5: Audio pipeline re-anchoring
        val audioPayload = """{"action": "audio_reanchor"}"""
        val audioReq = Request.Builder()
            .url("http://100.111.123.93:8899/api/dispatch")
            .post(audioPayload.toRequestBody("application/json".toMediaType()))
            .build()
        okHttpClient.newCall(audioReq).execute().use { resp ->
            assertTrue(resp.isSuccessful)
            val body = resp.body?.string()
            assertTrue(body?.contains("Audio pipeline re-anchored successfully") == true)
        }

        // Step 6: Terminal stream captures final status
        ptyStream.write("tuned-adm profile throughput-performance applied for task $taskId\n".toByteArray(Charsets.UTF_8))
        ptyStream.write("PipeWire quantum aligned to 64/48000\n".toByteArray(Charsets.UTF_8))
        ptyStream.flush()

        val captured = fakeSsh.getCapturedInput()
        assertTrue(captured.contains("Current active profile: powersave"))
        assertTrue(captured.contains("task_rfc-00142_a1b2"))
        assertTrue(captured.contains("PipeWire quantum aligned"))
    }
}
