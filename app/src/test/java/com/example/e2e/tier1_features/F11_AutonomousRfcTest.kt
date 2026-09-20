package com.example.e2e.tier1_features

import com.example.data.api.RfcVoteRequest
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1 Tests for Feature 11: Autonomous RFC Deck & Approvals.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F11_01: Ingestion and deserialization of proposed RFCs and execution steps
 * - T1_F11_02: 1-Click RFC approval voting lifecycle and task ID association
 * - T1_F11_03: 1-Click RFC rejection voting lifecycle
 * - T1_F11_04: RFC risk level classification (LOW, MEDIUM, HIGH)
 * - T1_F11_05: Approved RFC task ID tracking and lifecycle persistence
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F11_AutonomousRfcTest : E2eTestHarness() {

    @Test
    fun T1_F11_01_ingestion_of_proposed_rfcs_and_steps() = runBlocking {
        val rfcsResponse = apiService.getRfcs()
        assertNotNull(rfcsResponse)
        assertEquals(3, rfcsResponse.rfcs.size)

        val rfc = rfcsResponse.rfcs.find { it.id == "RFC-00142" }
        assertNotNull(rfc)
        assertEquals("SRE Autonomous Watchdog", rfc?.source)
        assertEquals("Perf Tune: CPU Frequency Scaling Governor Alignment", rfc?.title)
        assertEquals("OPTIMIZATION", rfc?.category)
        assertEquals("PROPOSED", rfc?.status)
        assertEquals("MEDIUM", rfc?.riskLevel)
        assertEquals(2, rfc?.proposedSteps?.size)
        assertEquals("tuned-adm profile throughput-performance", rfc?.proposedSteps?.get(0))
    }

    @Test
    fun T1_F11_02_1click_rfc_approval_dispatch() = runBlocking {
        val voteResponse = apiService.voteRfc("RFC-00142", RfcVoteRequest(decision = "approve"))
        assertNotNull(voteResponse)
        assertTrue(voteResponse.success)
        assertEquals("APPROVED", voteResponse.status)
        assertEquals("task_rfc-00142_a1b2", voteResponse.taskId)

        val lastReq = dispatcher.lastRecordedRequest
        assertNotNull(lastReq)
        assertEquals("/api/rfcs/RFC-00142/vote", lastReq?.url?.encodedPath)
        val body = dispatcher.extractRequestBody(lastReq!!)
        assertTrue(body.contains("approve"))
    }

    @Test
    fun T1_F11_03_1click_rfc_rejection_dispatch() = runBlocking {
        val voteResponse = apiService.voteRfc("RFC-00143", RfcVoteRequest(decision = "deny"))
        assertNotNull(voteResponse)
        assertTrue(voteResponse.success)
        assertEquals("REJECTED", voteResponse.status)

        val lastReq = dispatcher.lastRecordedRequest
        assertNotNull(lastReq)
        assertEquals("/api/rfcs/RFC-00143/vote", lastReq?.url?.encodedPath)
        val body = dispatcher.extractRequestBody(lastReq!!)
        assertTrue(body.contains("deny"))
    }

    @Test
    fun T1_F11_04_rfc_risk_level_classification() = runBlocking {
        val rfcs = apiService.getRfcs().rfcs
        val lowRfc = rfcs.find { it.riskLevel == "LOW" }
        val medRfc = rfcs.find { it.riskLevel == "MEDIUM" }
        val highRfc = rfcs.find { it.riskLevel == "HIGH" }

        assertNotNull(lowRfc)
        assertNotNull(medRfc)
        assertNotNull(highRfc)

        assertEquals("RFC-00143", lowRfc?.id)
        assertEquals("RFC-00142", medRfc?.id)
        assertEquals("RFC-00144", highRfc?.id)
    }

    @Test
    fun T1_F11_05_approved_rfc_task_id_tracking() = runBlocking {
        val response = apiService.voteRfc("RFC-00142", RfcVoteRequest(decision = "approve"))
        assertTrue(response.success)
        val taskId = response.taskId
        assertNotNull(taskId)
        assertTrue(taskId?.startsWith("task_rfc-00142") == true)
    }
}
