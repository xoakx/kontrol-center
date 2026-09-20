package com.example.e2e.tier5_adversarial

import com.example.data.api.ArcadeApiService
import com.example.data.api.RfcItem
import com.example.data.api.RfcVoteRequest
import com.example.data.api.RfcVoteResponse
import com.example.data.entity.RfcItemEntity
import com.example.data.repository.RfcRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.viewmodel.RfcFilter
import com.example.viewmodel.RfcViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class M4_AutonomousRfcStressTest : E2eTestHarness() {

    private lateinit var rfcRepository: RfcRepository
    private lateinit var viewModel: RfcViewModel

    @Before
    override fun setUp() {
        super.setUp()

        rfcRepository = RfcRepository(
            apiService = apiService,
            rfcDao = database.rfcDao(),
            hostProvider = null,
            sshConnectionManager = null,
            ioDispatcher = Dispatchers.IO
        )

        viewModel = RfcViewModel(
            rfcRepository = rfcRepository,
            defaultHostId = 1,
            scope = CoroutineScope(Dispatchers.IO)
        )
    }

    // =========================================================================
    // 1. OPTIMISTIC STATUS TRANSITIONS & IMMEDIATE ROOM DB PERSISTENCE
    // =========================================================================

    @Test(timeout = 10000)
    fun test01_optimisticStatusTransitions_and_immediateRoomPersistence_approval() = runBlocking {
        val entity = RfcItemEntity(
            id = 1,
            hostId = 1,
            rfcNumber = "RFC-00142",
            title = "Governor Tuning",
            description = "Tune governor to performance",
            proposedCommands = "tuned-adm profile throughput-performance",
            rollbackScript = "",
            impact = "MEDIUM",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        val inFlightLatch = CompletableDeferred<Unit>()
        val releaseLatch = CompletableDeferred<Unit>()

        dispatcher.overrideResponse("/api/rfcs/RFC-00142/vote") { req ->
            inFlightLatch.complete(Unit)
            runBlocking { releaseLatch.await() }
            dispatcher.setResponse("/api/rfcs/RFC-00142/vote", 200, """{"success": true, "rfc_id": "RFC-00142", "status": "APPROVED", "task_id": "task_142"}""")
            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"success": true, "rfc_id": "RFC-00142", "status": "APPROVED", "task_id": "task_142"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val voteJob = launch(Dispatchers.IO) {
            rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        }

        // Wait until network request has started and is suspended in-flight
        inFlightLatch.await()

        // Assert optimistic status transition immediately in StateFlow
        val inFlightStateFlowItem = rfcRepository.rfcs.value.find { it.id == "RFC-00142" }
        assertNotNull(inFlightStateFlowItem)
        assertEquals("APPROVED", inFlightStateFlowItem?.status)

        // Assert immediate persistence in Room DB before network completion
        val inFlightDbEntity = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertNotNull(inFlightDbEntity)
        assertEquals("APPROVED", inFlightDbEntity?.status)

        // Release network call
        releaseLatch.complete(Unit)
        voteJob.join()

        // Assert confirmed state
        val finalDbEntity = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertEquals("APPROVED", finalDbEntity?.status)
        assertEquals("task_142", finalDbEntity?.executionLog)
    }

    @Test(timeout = 10000)
    fun test02_optimisticStatusTransitions_and_immediateRoomPersistence_rejection() = runBlocking {
        val entity = RfcItemEntity(
            id = 2,
            hostId = 1,
            rfcNumber = "RFC-00143",
            title = "Cache Flush",
            description = "Flush cache",
            proposedCommands = "rm -rf ~/.cache/*",
            rollbackScript = "",
            impact = "LOW",
            status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        val inFlightLatch = CompletableDeferred<Unit>()
        val releaseLatch = CompletableDeferred<Unit>()

        dispatcher.overrideResponse("/api/rfcs/RFC-00143/vote") { req ->
            inFlightLatch.complete(Unit)
            runBlocking { releaseLatch.await() }
            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"success": true, "rfc_id": "RFC-00143", "status": "REJECTED"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val voteJob = launch(Dispatchers.IO) {
            rfcRepository.voteRfc("RFC-00143", "deny", hostId = 1)
        }

        inFlightLatch.await()

        // Assert optimistic REJECTED status in StateFlow
        val inFlightStateFlow = rfcRepository.rfcs.value.find { it.id == "RFC-00143" }
        assertEquals("REJECTED", inFlightStateFlow?.status)

        // Assert optimistic REJECTED status in Room DB
        val inFlightDbEntity = database.rfcDao().getRfcByNumberSync("RFC-00143")
        assertEquals("REJECTED", inFlightDbEntity?.status)

        releaseLatch.complete(Unit)
        voteJob.join()

        val finalDb = database.rfcDao().getRfcByNumberSync("RFC-00143")
        assertEquals("REJECTED", finalDb?.status)
    }

    // =========================================================================
    // 2. ROLLBACK VERIFICATION (409, 400, 404, 500, SOCKET TIMEOUT)
    // =========================================================================

    @Test(timeout = 10000)
    fun test03_rollbackOnHttp409Conflict_bothStateFlowAndRoomDb() = runBlocking {
        val entity = RfcItemEntity(
            id = 1, hostId = 1, rfcNumber = "RFC-00142",
            title = "Conflict Test", description = "Desc",
            proposedCommands = "cmd", rollbackScript = "",
            impact = "HIGH", status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.setResponse(
            "/api/rfcs/RFC-00142/vote",
            409,
            """{"detail": "Conflict: concurrent approval vote registered"}"""
        )

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isFailure)

        // Verify StateFlow rolled back to PROPOSED
        assertEquals("PROPOSED", rfcRepository.rfcs.value.find { it.id == "RFC-00142" }?.status)

        // Verify Room DB rolled back to PROPOSED
        val dbItem = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertNotNull(dbItem)
        assertEquals("PROPOSED", dbItem?.status)
    }

    @Test(timeout = 10000)
    fun test04_rollbackOnHttp400AlreadyExecuted_bothStateFlowAndRoomDb() = runBlocking {
        val entity = RfcItemEntity(
            id = 1, hostId = 1, rfcNumber = "RFC-00142",
            title = "Already Executed Test", description = "Desc",
            proposedCommands = "cmd", rollbackScript = "",
            impact = "LOW", status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.setResponse(
            "/api/rfcs/RFC-00142/vote",
            400,
            """{"detail": "RFC is already in EXECUTED state. No voting permitted."}"""
        )

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isFailure)

        assertEquals("PROPOSED", rfcRepository.rfcs.value.find { it.id == "RFC-00142" }?.status)
        val dbItem = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertEquals("PROPOSED", dbItem?.status)
    }

    @Test(timeout = 10000)
    fun test05_rollbackOnHttp404NotFound_bothStateFlowAndRoomDb() = runBlocking {
        val entity = RfcItemEntity(
            id = 1, hostId = 1, rfcNumber = "RFC-00142",
            title = "Not Found Test", description = "Desc",
            proposedCommands = "cmd", rollbackScript = "",
            impact = "MEDIUM", status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.setResponse(
            "/api/rfcs/RFC-00142/vote",
            404,
            """{"detail": "RFC RFC-00142 not found on remote server"}"""
        )

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isFailure)

        assertEquals("PROPOSED", rfcRepository.rfcs.value.find { it.id == "RFC-00142" }?.status)
        val dbItem = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertEquals("PROPOSED", dbItem?.status)
    }

    @Test(timeout = 10000)
    fun test06_rollbackOnHttp500InternalServerError_bothStateFlowAndRoomDb() = runBlocking {
        val entity = RfcItemEntity(
            id = 1, hostId = 1, rfcNumber = "RFC-00142",
            title = "500 Error Test", description = "Desc",
            proposedCommands = "cmd", rollbackScript = "",
            impact = "CRITICAL", status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.setResponse(
            "/api/rfcs/RFC-00142/vote",
            500,
            """{"detail": "Arcade Daemon internal failure: SQLite database locked"}"""
        )

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isFailure)

        assertEquals("PROPOSED", rfcRepository.rfcs.value.find { it.id == "RFC-00142" }?.status)
        val dbItem = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertEquals("PROPOSED", dbItem?.status)
    }

    @Test(timeout = 10000)
    fun test07_rollbackOnSocketTimeout_bothStateFlowAndRoomDb() = runBlocking {
        val entity = RfcItemEntity(
            id = 1, hostId = 1, rfcNumber = "RFC-00142",
            title = "Socket Timeout Test", description = "Desc",
            proposedCommands = "cmd", rollbackScript = "",
            impact = "HIGH", status = "PROPOSED"
        )
        database.rfcDao().insertRfc(entity)
        rfcRepository.loadCachedRfcs(1)

        dispatcher.overrideResponse("/api/rfcs/RFC-00142/vote") {
            throw SocketTimeoutException("Simulated connection/socket read timeout to 100.111.123.93:8899")
        }

        val result = rfcRepository.voteRfc("RFC-00142", "approve", hostId = 1)
        assertTrue(result.isFailure)

        assertEquals("PROPOSED", rfcRepository.rfcs.value.find { it.id == "RFC-00142" }?.status)
        val dbItem = database.rfcDao().getRfcByNumberSync("RFC-00142")
        assertEquals("PROPOSED", dbItem?.status)
    }

    // =========================================================================
    // 3. CONCURRENCY & DEBOUNCING (RAPID CONCURRENT VOTES)
    // =========================================================================

    @Test(timeout = 10000)
    fun test08_concurrencyAndDebouncing_rapidConcurrentVotesOnSameRfc() = runBlocking {
        viewModel.refresh().join()

        val networkCallCount = AtomicInteger(0)
        val inFlightGate = CompletableDeferred<Unit>()

        dispatcher.overrideResponse("/api/rfcs/RFC-00142/vote") { req ->
            networkCallCount.incrementAndGet()
            runBlocking { inFlightGate.await() }
            okhttp3.Response.Builder()
                .request(req)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"success": true, "rfc_id": "RFC-00142", "status": "APPROVED", "task_id": "t1"}""".toResponseBody("application/json".toMediaType()))
                .build()
        }

        // Trigger first vote (enters in-flight)
        val job1 = viewModel.approveRfc("RFC-00142")

        // Trigger 10 rapid concurrent approve/reject votes on the same RFC while job1 is in-flight
        val debouncedJobs = (1..10).map { i ->
            if (i % 2 == 0) {
                viewModel.approveRfc("RFC-00142")
            } else {
                viewModel.rejectRfc("RFC-00142")
            }
        }

        // Verify debounce indicator
        assertEquals("RFC-00142", viewModel.uiState.value.votingInProgressId)

        // Release the network response
        inFlightGate.complete(Unit)

        job1.join()
        debouncedJobs.joinAll()

        // Assert strictly 1 network vote proceeded
        assertEquals(1, networkCallCount.get())
        assertNull(viewModel.uiState.value.votingInProgressId)
        assertEquals("RFC-00142 Approved", viewModel.uiState.value.userNotice)
    }

    // =========================================================================
    // 4. OFFLINE RESILIENCE (ROOM CACHED PROPOSALS & IS_OFFLINE FLAG)
    // =========================================================================

    @Test(timeout = 10000)
    fun test09_offlineResilience_roomCachedProposalsServedAndIsOfflineFlag() = runBlocking {
        // Await initial refresh from setUp so background network insertion completes
        viewModel.refresh().join()

        // Clear initial seed from setUp
        database.rfcDao().clearRfcsForHost(1)

        // 1. Seed Room DB with cached RFCs
        val cached1 = RfcItemEntity(
            id = 101, hostId = 1, rfcNumber = "RFC-OFFLINE-1",
            title = "Cached Offline RFC 1", description = "Desc 1",
            proposedCommands = "cmd1", rollbackScript = "",
            impact = "LOW", status = "PROPOSED"
        )
        val cached2 = RfcItemEntity(
            id = 102, hostId = 1, rfcNumber = "RFC-OFFLINE-2",
            title = "Cached Offline RFC 2", description = "Desc 2",
            proposedCommands = "cmd2", rollbackScript = "",
            impact = "CRITICAL", status = "PROPOSED"
        )
        database.rfcDao().insertRfcs(listOf(cached1, cached2))

        // 2. Simulate complete network outage
        dispatcher.simulateNetworkFailure = true
        dispatcher.customFailureException = SocketTimeoutException("Mesh node unreachable")

        // 3. Attempt refresh via ViewModel
        viewModel.refresh(1).join()

        // Verify repository state immediately
        assertTrue("Repository isOffline must be true", rfcRepository.isOffline.value)
        assertEquals("Repository served Room cache", 2, rfcRepository.rfcs.value.size)

        // 4. Assert offline resilience in ViewModel:
        var state = viewModel.uiState.value
        var attempts = 0
        while (state.rfcs.size != 2 && attempts < 25) {
            kotlinx.coroutines.delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        assertTrue("isOffline must be true", state.isOffline)
        assertNotNull(state.errorMessage)
        assertEquals("Room cached proposals must be served", 2, state.rfcs.size)
        assertEquals("RFC-OFFLINE-2", state.rfcs[0].id)
        assertEquals("RFC-OFFLINE-1", state.rfcs[1].id)

        // 5. Restore network
        dispatcher.simulateNetworkFailure = false
        dispatcher.customFailureException = null

        // 6. Refresh again
        viewModel.refresh(1).join()

        var recoveredState = viewModel.uiState.value
        attempts = 0
        while (recoveredState.rfcs.size != 3 && attempts < 25) {
            kotlinx.coroutines.delay(20)
            recoveredState = viewModel.uiState.value
            attempts++
        }

        assertFalse("isOffline must be false after recovery", recoveredState.isOffline)
        assertNull(recoveredState.errorMessage)
        // Default MockArcadeDispatcher provides 3 RFCs
        assertEquals(3, recoveredState.rfcs.size)
    }

    // =========================================================================
    // 5. FILTER & SEARCH VERIFICATION (RISK LEVELS & KEYWORDS)
    // =========================================================================

    @Test(timeout = 10000)
    fun test10_filterByRisk_lowMediumHighCritical() = runBlocking {
        viewModel.refresh().join()
        database.rfcDao().clearRfcsForHost(1)

        val r1 = RfcItemEntity(id = 1, hostId = 1, rfcNumber = "RFC-L", title = "Low Risk", description = "Desc", proposedCommands = "c1", rollbackScript = "", impact = "LOW", status = "PROPOSED")
        val r2 = RfcItemEntity(id = 2, hostId = 1, rfcNumber = "RFC-M", title = "Med Risk", description = "Desc", proposedCommands = "c2", rollbackScript = "", impact = "MEDIUM", status = "PROPOSED")
        val r3 = RfcItemEntity(id = 3, hostId = 1, rfcNumber = "RFC-H", title = "High Risk", description = "Desc", proposedCommands = "c3", rollbackScript = "", impact = "HIGH", status = "PROPOSED")
        val r4 = RfcItemEntity(id = 4, hostId = 1, rfcNumber = "RFC-C", title = "Crit Risk", description = "Desc", proposedCommands = "c4", rollbackScript = "", impact = "CRITICAL", status = "PROPOSED")
        database.rfcDao().insertRfcs(listOf(r1, r2, r3, r4))

        // Preload via repository fallback
        dispatcher.setResponse("/api/rfcs", 500, """{"detail": "Use cache"}""")
        viewModel.refresh().join()

        var state = viewModel.uiState.value
        var attempts = 0
        while (state.rfcs.size != 4 && attempts < 25) {
            kotlinx.coroutines.delay(20)
            state = viewModel.uiState.value
            attempts++
        }

        assertEquals(4, state.rfcs.size)

        // Test LOW risk filter
        viewModel.setRiskFilter("LOW")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-L", viewModel.uiState.value.filteredRfcs[0].id)

        // Test MEDIUM risk filter
        viewModel.setRiskFilter("MEDIUM")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-M", viewModel.uiState.value.filteredRfcs[0].id)

        // Test HIGH risk filter
        viewModel.setRiskFilter("HIGH")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-H", viewModel.uiState.value.filteredRfcs[0].id)

        // Test CRITICAL risk filter
        viewModel.setRiskFilter("CRITICAL")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-C", viewModel.uiState.value.filteredRfcs[0].id)

        // Clear filter
        viewModel.setRiskFilter(null)
        assertEquals(4, viewModel.uiState.value.filteredRfcs.size)
    }

    @Test(timeout = 10000)
    fun test11_keywordSearch_acrossTitleDescriptionAndProposedSteps() = runBlocking {
        viewModel.refresh().join()
        database.rfcDao().clearRfcsForHost(1)

        val r1 = RfcItemEntity(
            id = 1, hostId = 1, rfcNumber = "RFC-101",
            title = "Frequency Governor Alignment",
            description = "CPU frequency governor switch",
            proposedCommands = "cpupower frequency-set -g powersave",
            rollbackScript = "", impact = "LOW", status = "PROPOSED"
        )
        val r2 = RfcItemEntity(
            id = 2, hostId = 1, rfcNumber = "RFC-102",
            title = "Cache Maintenance",
            description = "Drop clean filesystem pages",
            proposedCommands = "sync\necho 1 > /proc/sys/vm/drop_caches",
            rollbackScript = "", impact = "MEDIUM", status = "PROPOSED"
        )
        val r3 = RfcItemEntity(
            id = 3, hostId = 1, rfcNumber = "RFC-103",
            title = "Firewall Hardening",
            description = "Block brute-force ingress",
            proposedCommands = "nft add element inet filter blocklist { 198.51.100.0/24 }",
            rollbackScript = "", impact = "HIGH", status = "PROPOSED"
        )
        val r4 = RfcItemEntity(
            id = 4, hostId = 1, rfcNumber = "RFC-104",
            title = "Livepatch Panic Mitigation",
            description = "Apply urgent emergency patch",
            proposedCommands = "kpatch load urgent-patch.ko",
            rollbackScript = "", impact = "CRITICAL", status = "PROPOSED"
        )
        database.rfcDao().insertRfcs(listOf(r1, r2, r3, r4))

        dispatcher.setResponse("/api/rfcs", 500, """{"detail": "Use cache"}""")
        viewModel.refresh().join()

        var attempts = 0
        while (viewModel.uiState.value.rfcs.size != 4 && attempts < 25) {
            kotlinx.coroutines.delay(20)
            attempts++
        }

        // 1. Search across Title
        viewModel.setSearchQuery("Maintenance")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-102", viewModel.uiState.value.filteredRfcs[0].id)

        // 2. Search across Description
        viewModel.setSearchQuery("urgent emergency")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-104", viewModel.uiState.value.filteredRfcs[0].id)

        // 3. Search across Proposed Steps
        viewModel.setSearchQuery("drop_caches")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-102", viewModel.uiState.value.filteredRfcs[0].id)

        viewModel.setSearchQuery("blocklist")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-103", viewModel.uiState.value.filteredRfcs[0].id)

        // 4. Search across RFC ID
        viewModel.setSearchQuery("RFC-101")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-101", viewModel.uiState.value.filteredRfcs[0].id)

        // 5. Non-matching query
        viewModel.setSearchQuery("nonexistent_keyword_xyz")
        assertEquals(0, viewModel.uiState.value.filteredRfcs.size)

        // 6. Combined Search Query + Risk Filter
        viewModel.setRiskFilter("CRITICAL")
        viewModel.setSearchQuery("Livepatch")
        assertEquals(1, viewModel.uiState.value.filteredRfcs.size)
        assertEquals("RFC-104", viewModel.uiState.value.filteredRfcs[0].id)

        // Conflicting filter: searching for Livepatch under LOW risk -> 0 matches
        viewModel.setRiskFilter("LOW")
        assertEquals(0, viewModel.uiState.value.filteredRfcs.size)

        // Reset
        viewModel.setRiskFilter(null)
        viewModel.setSearchQuery("")
        assertEquals(4, viewModel.uiState.value.filteredRfcs.size)
    }
}
