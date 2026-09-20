package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RfcDtoParsingTest {

    private lateinit var moshi: Moshi

    @Before
    fun setUp() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    @Test
    fun testRfcsResponse_FullPayloadDeserialization() {
        val json = """
        {
          "rfcs": [
            {
              "id": "RFC-00142",
              "source": "SRE Autonomous Watchdog",
              "title": "Perf Tune: CPU Frequency Scaling",
              "category": "OPTIMIZATION",
              "description": "Lock CPU governor to throughput-performance",
              "proposed_steps": [
                "tuned-adm profile throughput-performance",
                "echo performance > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
              ],
              "risk_level": "MEDIUM",
              "status": "PROPOSED",
              "task_id": "task_123",
              "created_at": "2026-09-20T00:01:00Z",
              "updated_at": "2026-09-20T00:02:00Z",
              "resolved_by": "operator"
            }
          ]
        }
        """.trimIndent()

        val adapter = moshi.adapter(RfcsResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertEquals(1, response?.rfcs?.size)

        val rfc = response?.rfcs?.get(0)
        assertNotNull(rfc)
        assertEquals("RFC-00142", rfc?.id)
        assertEquals("SRE Autonomous Watchdog", rfc?.source)
        assertEquals("Perf Tune: CPU Frequency Scaling", rfc?.title)
        assertEquals("OPTIMIZATION", rfc?.category)
        assertEquals("Lock CPU governor to throughput-performance", rfc?.description)
        assertEquals(2, rfc?.proposedSteps?.size)
        assertEquals("MEDIUM", rfc?.riskLevel)
        assertEquals("PROPOSED", rfc?.status)
        assertEquals("task_123", rfc?.taskId)
        assertEquals("2026-09-20T00:01:00Z", rfc?.createdAt)
        assertEquals("2026-09-20T00:02:00Z", rfc?.updatedAt)
        assertEquals("operator", rfc?.resolvedBy)
    }

    @Test
    fun testRfcItem_OptionalFields_NullTaskIdAndResolvedBy() {
        val json = """
        {
          "id": "RFC-00143",
          "source": "Audit Daemon",
          "title": "Security Hardening",
          "category": "SECURITY",
          "description": "Disable cups-browsed",
          "proposed_steps": ["systemctl stop cups-browsed"],
          "risk_level": "LOW",
          "status": "PROPOSED",
          "task_id": null,
          "created_at": "2026-09-20T00:00:00Z",
          "updated_at": "2026-09-20T00:00:00Z",
          "resolved_by": null
        }
        """.trimIndent()

        val adapter = moshi.adapter(RfcItem::class.java)
        val item = adapter.fromJson(json)

        assertNotNull(item)
        assertEquals("RFC-00143", item?.id)
        assertNull(item?.taskId)
        assertNull(item?.resolvedBy)
    }

    @Test
    fun testRfcItem_EmptyProposedSteps() {
        val json = """
        {
          "id": "RFC-00199",
          "source": "Manual Advisory",
          "title": "Advisory Note",
          "category": "ADVISORY",
          "description": "Advisory only",
          "proposed_steps": [],
          "created_at": "2026-09-20T00:00:00Z",
          "updated_at": "2026-09-20T00:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(RfcItem::class.java)
        val item = adapter.fromJson(json)

        assertNotNull(item)
        assertNotNull(item?.proposedSteps)
        assertTrue(item?.proposedSteps?.isEmpty() == true)
        assertEquals("MEDIUM", item?.riskLevel)
        assertEquals("PROPOSED", item?.status)
    }

    @Test
    fun testRfcVoteRequest_Serialization() {
        val request = RfcVoteRequest(decision = "approve")
        val adapter = moshi.adapter(RfcVoteRequest::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.contains("\"decision\":\"approve\""))
    }

    @Test
    fun testRfcVoteRequest_DenySerialization() {
        val request = RfcVoteRequest(decision = "deny")
        val adapter = moshi.adapter(RfcVoteRequest::class.java)
        val json = adapter.toJson(request)

        assertTrue(json.contains("\"decision\":\"deny\""))
    }

    @Test
    fun testRfcVoteResponse_Deserialization() {
        val json = """
        {
          "success": true,
          "status": "APPROVED",
          "task_id": "task_rfc-00142_a1b2"
        }
        """.trimIndent()

        val adapter = moshi.adapter(RfcVoteResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response?.success == true)
        assertEquals("APPROVED", response?.status)
        assertEquals("task_rfc-00142_a1b2", response?.taskId)
    }

    @Test
    fun testRfcVoteResponse_NullTaskId() {
        val json = """
        {
          "success": true,
          "status": "REJECTED"
        }
        """.trimIndent()

        val adapter = moshi.adapter(RfcVoteResponse::class.java)
        val response = adapter.fromJson(json)

        assertNotNull(response)
        assertTrue(response?.success == true)
        assertEquals("REJECTED", response?.status)
        assertNull(response?.taskId)
    }

    @Test
    fun testRfcItem_DefaultRiskLevel() {
        val json = """
        {
          "id": "RFC-00150",
          "source": "Scribe",
          "title": "Doc link heal",
          "category": "MAINTENANCE",
          "description": "Heal markdown deadlinks",
          "created_at": "2026-09-20T00:00:00Z",
          "updated_at": "2026-09-20T00:00:00Z"
        }
        """.trimIndent()

        val adapter = moshi.adapter(RfcItem::class.java)
        val item = adapter.fromJson(json)

        assertNotNull(item)
        assertEquals("MEDIUM", item?.riskLevel)
        assertEquals("PROPOSED", item?.status)
        assertTrue(item?.proposedSteps?.isEmpty() == true)
    }
}
