package com.example.e2e.tier2_boundaries

import com.example.data.api.RfcVoteRequest
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException

/**
 * Tier 2 Boundary Tests: B11 Autonomous RFC Deck Boundary.
 * Covers 5 boundary conditions:
 * - T2_B11_01: Conflicting concurrent votes returning HTTP 409 Conflict
 * - T2_B11_02: Vote on already-executed or finalized RFC returning error
 * - T2_B11_03: Empty proposed steps diff handling without index out of bounds
 * - T2_B11_04: Extremely long (64KB) bash script remediation step payload
 * - T2_B11_05: Deleted or non-existent RFC ID vote attempt (HTTP 404)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B11_AutonomousRfcBoundaryTest : E2eTestHarness() {

    @Test
    fun T2_B11_01_conflicting_concurrent_votes_409() = runBlocking {
        dispatcher.setResponse(
            "/api/rfcs/RFC-00142/vote",
            409,
            """{"detail": "RFC-00142 state conflict: concurrent vote already registered."}"""
        )

        try {
            apiService.voteRfc("RFC-00142", RfcVoteRequest(decision = "approve"))
            fail("Expected HttpException(409)")
        } catch (e: HttpException) {
            assertEquals(409, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("concurrent vote already registered") == true)
        }
    }

    @Test
    fun T2_B11_02_vote_on_already_executed_rfc() = runBlocking {
        dispatcher.setResponse(
            "/api/rfcs/RFC-00140/vote",
            400,
            """{"detail": "RFC-00140 is already in EXECUTED status. Further votes rejected."}"""
        )

        try {
            apiService.voteRfc("RFC-00140", RfcVoteRequest(decision = "approve"))
            fail("Expected HttpException(400)")
        } catch (e: HttpException) {
            assertEquals(400, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("already in EXECUTED status") == true)
        }
    }

    @Test
    fun T2_B11_03_empty_proposed_steps_diff() = runBlocking {
        val emptyStepsRfcPayload = """
        {
          "rfcs": [
            {
              "id": "RFC-00199",
              "source": "Manual",
              "title": "Ad-Hoc Advisory Only",
              "category": "ADVISORY",
              "description": "Advisory diff",
              "created_at": "2026-09-19T18:00:00Z",
              "updated_at": "2026-09-19T18:00:00Z",
              "status": "PROPOSED",
              "risk_level": "LOW",
              "proposed_steps": []
            }
          ]
        }
        """.trimIndent()

        dispatcher.setResponse("/api/rfcs", 200, emptyStepsRfcPayload)

        val rfcs = apiService.getRfcs().rfcs
        assertEquals(1, rfcs.size)
        val rfc = rfcs[0]
        assertNotNull(rfc.proposedSteps)
        assertTrue(rfc.proposedSteps.isEmpty())
    }

    @Test
    fun T2_B11_04_extremely_long_bash_script_payload() = runBlocking {
        // Build 64 Kilobytes bash script payload
        val scriptLine = "echo 'Performing deep sysctl parameter audit step'\n"
        val scriptContent = scriptLine.repeat(((64 * 1024) / scriptLine.length) + 1)
        assertTrue(scriptContent.length >= 64 * 1024)

        // Escape newlines for JSON embedding
        val escapedScript = scriptContent.replace("\n", "\\n").replace("'", "\\'")
        val hugeRfcPayload = """
        {
          "rfcs": [
            {
              "id": "RFC-00200",
              "source": "SRE Deep Tuner",
              "title": "Comprehensive Kernel Tune",
              "category": "OPTIMIZATION",
              "description": "Huge script diff",
              "created_at": "2026-09-19T18:00:00Z",
              "updated_at": "2026-09-19T18:00:00Z",
              "status": "PROPOSED",
              "risk_level": "HIGH",
              "proposed_steps": ["$escapedScript"]
            }
          ]
        }
        """.trimIndent()

        dispatcher.setResponse("/api/rfcs", 200, hugeRfcPayload)

        val rfcs = apiService.getRfcs().rfcs
        assertEquals(1, rfcs.size)
        val step = rfcs[0].proposedSteps?.get(0)
        assertNotNull(step)
        assertTrue(step?.length ?: 0 >= 64 * 1024)
    }

    @Test
    fun T2_B11_05_deleted_rfc_id_not_found_404() = runBlocking {
        dispatcher.setResponse(
            "/api/rfcs/RFC-NONEXISTENT/vote",
            404,
            """{"detail": "RFC 'RFC-NONEXISTENT' not found"}"""
        )

        try {
            apiService.voteRfc("RFC-NONEXISTENT", RfcVoteRequest(decision = "approve"))
            fail("Expected HttpException(404)")
        } catch (e: HttpException) {
            assertEquals(404, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("not found") == true)
        }
    }
}
