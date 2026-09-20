package com.example.data.repository

import com.example.data.entity.RfcItemEntity
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RfcRepositoryTest : E2eTestHarness() {

    private lateinit var rfcRepository: RfcRepository

    @Before
    override fun setUp() {
        super.setUp()
        rfcRepository = RfcRepository(
            apiService = apiService,
            rfcDao = database.rfcDao(),
            hostProvider = null,
            sshConnectionManager = null,
            ioDispatcher = kotlinx.coroutines.Dispatchers.IO
        )
    }

    @Test
    fun testGetRfcs_EmitsRoomCachedEntities() = runBlocking {
        val entity1 = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00101",
            title = "Cached Optimization",
            description = "Cached description",
            proposedCommands = "sysctl -w net.core.somaxconn=1024",
            rollbackScript = "",
            impact = "LOW",
            status = "PROPOSED"
        )
        val entity2 = RfcItemEntity(
            id = 2,
            hostId = 1,
            rfcNumber = "RFC-00102",
            title = "Cached Security",
            description = "Cached firewall rule",
            proposedCommands = "ufw enable",
            rollbackScript = "",
            impact = "MEDIUM",
            status = "APPROVED"
        )

        database.rfcDao().insertRfcs(listOf(entity1, entity2))

        val result = rfcRepository.getRfcs(hostId = 1)
        assertTrue(result.isSuccess)
        val rfcs = result.getOrNull()
        assertNotNull(rfcs)
        assertEquals(2, rfcs?.size)
        assertEquals("RFC-00102", rfcs?.get(0)?.id)
        assertEquals("RFC-00101", rfcs?.get(1)?.id)
    }

    @Test
    fun testRefreshRfcs_FetchesArcadeApiAndUpsertsRoomDb() = runBlocking {
        // MockArcadeDispatcher provides 3 default RFCs (RFC-00142, RFC-00143, RFC-00144)
        val result = rfcRepository.refreshRfcs(hostId = 1)
        assertTrue(result.isSuccess)
        val rfcs = result.getOrNull()
        assertNotNull(rfcs)
        assertEquals(3, rfcs?.size)

        // Verify Room persistence
        val cached = database.rfcDao().getRfcsForHost(1).first()
        assertEquals(3, cached.size)
        assertTrue(cached.any { it.rfcNumber == "RFC-00142" })
    }

    @Test
    fun testRefreshRfcs_NetworkFailurePreservesRoomCache() = runBlocking {
        val entity = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00999",
            title = "Persistent RFC",
            description = "Should not be lost on network failure",
            proposedCommands = "echo 1",
            rollbackScript = "",
            impact = "LOW",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)

        dispatcher.setResponse("/api/rfcs", 500, """{"detail": "Internal Server Error"}""")

        val result = rfcRepository.refreshRfcs(hostId = 1)
        assertTrue(result.isFailure)
        assertTrue(rfcRepository.isOffline.value)

        // Cached entities preserved in Room
        val cached = database.rfcDao().getRfcsForHost(1).first()
        assertEquals(1, cached.size)
        assertEquals("RFC-00999", cached[0].rfcNumber)

        // Repository in-memory state loaded from fallback
        assertEquals(1, rfcRepository.rfcs.value.size)
        assertEquals("RFC-00999", rfcRepository.rfcs.value[0].id)
    }

    @Test
    fun testVoteRfc_ApproveSuccess_OptimisticAndConfirm() = runBlocking {
        val entity = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00142",
            title = "CPU Governor",
            description = "Tune governor",
            proposedCommands = "tuned-adm profile throughput-performance",
            rollbackScript = "",
            impact = "MEDIUM",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isSuccess)
        val voteResp = result.getOrNull()
        assertNotNull(voteResp)
        assertEquals("APPROVED", voteResp?.status)

        // Verify Room DB status confirmed
        val updated = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertNotNull(updated)
        assertEquals("APPROVED", updated?.status)
        assertEquals("task_rfc-00142_a1b2", updated?.executionLog)
        assertNotNull(updated?.executedAt)
    }

    @Test
    fun testVoteRfc_RejectSuccess_OptimisticAndConfirm() = runBlocking {
        val entity = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00143",
            title = "Unwanted Service",
            description = "Disable service",
            proposedCommands = "systemctl stop test",
            rollbackScript = "",
            impact = "LOW",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        val result = rfcRepository.voteRfc("RFC-00143", "deny", hostId = 1)
        assertTrue(result.isSuccess)
        val voteResp = result.getOrNull()
        assertNotNull(voteResp)
        assertEquals("REJECTED", voteResp?.status)

        val updated = database.rfcDao().getRfcByNumberSync("RFC-00143")
        assertNotNull(updated)
        assertEquals("REJECTED", updated?.status)
    }

    @Test
    fun testVoteRfc_Conflict409_RollsBackToOriginalStatus() = runBlocking {
        val entity = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00142",
            title = "Conflict RFC",
            description = "Will trigger 409",
            proposedCommands = "cmd",
            rollbackScript = "",
            impact = "MEDIUM",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.setResponse(
            "/api/rfcs/RFC-00142/vote",
            409,
            """{"detail": "RFC-00142 state conflict: concurrent vote already registered."}"""
        )

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isFailure)

        // Assert state was rolled back to PROPOSED in Room DB
        val rolledBack = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertNotNull(rolledBack)
        assertEquals("PROPOSED", rolledBack?.status)

        // Assert in-memory state was rolled back
        val memItem = rfcRepository.rfcs.value.find { it.id == "RFC-00142" }
        assertEquals("PROPOSED", memItem?.status)
    }

    @Test
    fun testVoteRfc_AlreadyExecuted400_RollsBackToOriginalStatus() = runBlocking {
        val entity = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00140",
            title = "Finalized RFC",
            description = "Already executed",
            proposedCommands = "cmd",
            rollbackScript = "",
            impact = "LOW",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.setResponse(
            "/api/rfcs/RFC-00140/vote",
            400,
            """{"detail": "RFC-00140 is already in EXECUTED status. Further votes rejected."}"""
        )

        val result = rfcRepository.voteRfc("RFC-00140", "approve", hostId = 1)
        assertTrue(result.isFailure)

        val rolledBack = database.rfcDao().getRfcByNumberSync("RFC-00140")
        assertEquals("PROPOSED", rolledBack?.status)
    }

    @Test
    fun testVoteRfc_NotFound404_RollsBack() = runBlocking {
        dispatcher.setResponse(
            "/api/rfcs/RFC-NONEXISTENT/vote",
            404,
            """{"detail": "RFC 'RFC-NONEXISTENT' not found"}"""
        )

        val result = rfcRepository.voteRfc("RFC-NONEXISTENT", "approve", hostId = 1)
        assertTrue(result.isFailure)
    }

    @Test
    fun testVoteRfc_NetworkTimeout_RollsBack() = runBlocking {
        val entity = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00142",
            title = "Timeout RFC",
            description = "Server timeout",
            proposedCommands = "cmd",
            rollbackScript = "",
            impact = "HIGH",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.setResponse("/api/rfcs/RFC-00142/vote", 504, """{"detail": "Gateway Timeout"}""")

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isFailure)

        val rolledBack = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertEquals("PROPOSED", rolledBack?.status)
    }

    @Test
    fun testPendingAndHistoryRfcsFiltering() = runBlocking {
        val entity1 = RfcItemEntity(id = 1, hostId = 1, rfcNumber = "RFC-1", title = "P1", description = "", proposedCommands = "", rollbackScript = "", impact = "LOW", status = "PROPOSED")
        val entity2 = RfcItemEntity(id = 2, hostId = 1, rfcNumber = "RFC-2", title = "P2", description = "", proposedCommands = "", rollbackScript = "", impact = "MEDIUM", status = "APPROVED")
        val entity3 = RfcItemEntity(id = 3, hostId = 1, rfcNumber = "RFC-3", title = "P3", description = "", proposedCommands = "", rollbackScript = "", impact = "HIGH", status = "REJECTED")

        database.rfcDao().insertRfcs(listOf(entity1, entity2, entity3))
        rfcRepository.loadCachedRfcs(1)

        assertEquals(3, rfcRepository.rfcs.value.size)
        assertEquals(1, rfcRepository.pendingRfcs.value.size)
        assertEquals("RFC-1", rfcRepository.pendingRfcs.value[0].id)
        assertEquals(2, rfcRepository.historyRfcs.value.size)
    }

    @Test
    fun testEmptyProposedStepsHandled() {
        val entity = RfcItemEntity(id = 1, hostId = 1, rfcNumber = "RFC-EMPTY", title = "Empty", description = "", proposedCommands = "", rollbackScript = "", impact = "LOW", status = "PROPOSED")
        val item = RfcRepository.entityToRfcItem(entity)
        assertTrue(item.proposedSteps.isEmpty())
    }

    @Test
    fun testClearRfcsForHost() = runBlocking {
        val h1 = RfcItemEntity(id = 1, hostId = 1, rfcNumber = "RFC-H1", title = "H1", description = "", proposedCommands = "", rollbackScript = "", impact = "LOW", status = "PROPOSED")
        val h2 = RfcItemEntity(id = 2, hostId = 2, rfcNumber = "RFC-H2", title = "H2", description = "", proposedCommands = "", rollbackScript = "", impact = "LOW", status = "PROPOSED")
        database.rfcDao().insertRfcs(listOf(h1, h2))

        database.rfcDao().clearRfcsForHost(1)

        val host1Remaining = database.rfcDao().getRfcsForHost(1).first()
        val host2Remaining = database.rfcDao().getRfcsForHost(2).first()
        assertTrue(host1Remaining.isEmpty())
        assertEquals(1, host2Remaining.size)
    }

    @Test
    fun testUpdateRfcStatusByNumber() = runBlocking {
        val entity = RfcItemEntity(id = 1, hostId = 1, rfcNumber = "RFC-UPDATE", title = "Title", description = "", proposedCommands = "", rollbackScript = "", impact = "LOW", status = "PROPOSED")
        database.rfcDao().insertRfc(entity)

        val now = System.currentTimeMillis()
        database.rfcDao().updateRfcStatusByNumber("RFC-UPDATE", "EXECUTED", "task_custom_123", now)

        val updated = database.rfcDao().getRfcByNumberSync("RFC-UPDATE")
        assertNotNull(updated)
        assertEquals("EXECUTED", updated?.status)
        assertEquals("task_custom_123", updated?.executionLog)
        assertEquals(now, updated?.executedAt)
    }

    @Test
    fun testGetRfcByNumberFlow() = runBlocking {
        val entity = RfcItemEntity(id = 1, hostId = 1, rfcNumber = "RFC-FLOW", title = "Flow Title", description = "", proposedCommands = "", rollbackScript = "", impact = "HIGH", status = "PROPOSED")
        database.rfcDao().insertRfc(entity)

        val flowItem = database.rfcDao().getRfcByNumber("RFC-FLOW").first()
        assertNotNull(flowItem)
        assertEquals("Flow Title", flowItem?.title)
    }
}
