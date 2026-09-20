package com.example.viewmodel

import com.example.data.entity.RfcItemEntity
import com.example.data.repository.RfcRepository
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RfcViewModelTest : E2eTestHarness() {

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
            scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        )
    }

    @Test(timeout = 10000)
    fun testInitialLoad_Success() = runBlocking {
        viewModel.refresh().join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(3, state.rfcs.size)
        assertEquals(3, state.pendingRfcs.size)
        assertEquals(0, state.historyRfcs.size)
        // Default filter is PENDING, so filteredRfcs should have 3
        assertEquals(3, state.filteredRfcs.size)
        assertFalse(state.isOffline)
        assertNull(state.errorMessage)
    }

    @Test(timeout = 10000)
    fun testTabSwitching_UpdatesFilterAndList() = runBlocking {
        viewModel.refresh().join()

        // Switch to History tab (tab 1)
        viewModel.setSelectedTab(1)
        var state = viewModel.uiState.value
        assertEquals(1, state.selectedTab)
        assertEquals(RfcFilter.ALL, state.selectedFilter)
        assertEquals(0, state.filteredRfcs.size)

        // Switch back to Pending tab (tab 0)
        viewModel.setSelectedTab(0)
        state = viewModel.uiState.value
        assertEquals(0, state.selectedTab)
        assertEquals(RfcFilter.PENDING, state.selectedFilter)
        assertEquals(3, state.filteredRfcs.size)
    }

    @Test(timeout = 10000)
    fun testFilterStatus_ApprovedAndRejected() = runBlocking {
        viewModel.refresh().join()

        // Initially no approved or rejected
        viewModel.setFilter(RfcFilter.APPROVED)
        var state = viewModel.uiState.value
        assertEquals(RfcFilter.APPROVED, state.selectedFilter)
        assertEquals(0, state.filteredRfcs.size)

        // Approve one
        viewModel.approveRfc("RFC-00142").join()
        state = viewModel.uiState.value
        assertEquals(1, state.filteredRfcs.size)
        assertEquals("RFC-00142", state.filteredRfcs[0].id)

        // Reject one
        viewModel.rejectRfc("RFC-00143").join()
        viewModel.setFilter(RfcFilter.REJECTED)
        state = viewModel.uiState.value
        assertEquals(RfcFilter.REJECTED, state.selectedFilter)
        assertEquals(1, state.filteredRfcs.size)
        assertEquals("RFC-00143", state.filteredRfcs[0].id)
    }

    @Test(timeout = 10000)
    fun testRiskFilter() = runBlocking {
        viewModel.refresh().join()

        // Filter by MEDIUM risk (RFC-00142 is MEDIUM)
        viewModel.setRiskFilter("MEDIUM")
        var state = viewModel.uiState.value
        assertEquals("MEDIUM", state.selectedRiskFilter)
        assertEquals(1, state.filteredRfcs.size)
        assertEquals("RFC-00142", state.filteredRfcs[0].id)

        // Filter by LOW risk (RFC-00143 is LOW)
        viewModel.setRiskFilter("LOW")
        state = viewModel.uiState.value
        assertEquals(1, state.filteredRfcs.size)
        assertEquals("RFC-00143", state.filteredRfcs[0].id)

        // Filter by CRITICAL risk (none in default mock)
        viewModel.setRiskFilter("CRITICAL")
        state = viewModel.uiState.value
        assertEquals(0, state.filteredRfcs.size)

        // Clear risk filter
        viewModel.setRiskFilter(null)
        state = viewModel.uiState.value
        assertNull(state.selectedRiskFilter)
        assertEquals(3, state.filteredRfcs.size)
    }

    @Test(timeout = 10000)
    fun testSearchQueryFilter() = runBlocking {
        viewModel.refresh().join()

        // Search by RFC ID
        viewModel.setSearchQuery("142")
        var state = viewModel.uiState.value
        assertEquals(1, state.filteredRfcs.size)
        assertEquals("RFC-00142", state.filteredRfcs[0].id)

        // Search by keyword in title/desc
        viewModel.setSearchQuery("governor")
        state = viewModel.uiState.value
        assertEquals(1, state.filteredRfcs.size)
        assertEquals("RFC-00142", state.filteredRfcs[0].id)

        // Search by proposed steps
        viewModel.setSearchQuery("tuned-adm")
        state = viewModel.uiState.value
        assertEquals(1, state.filteredRfcs.size)
        assertEquals("RFC-00142", state.filteredRfcs[0].id)

        // Non-matching query
        viewModel.setSearchQuery("xyznonexistent")
        state = viewModel.uiState.value
        assertEquals(0, state.filteredRfcs.size)

        // Clear search
        viewModel.setSearchQuery("")
        state = viewModel.uiState.value
        assertEquals(3, state.filteredRfcs.size)
    }

    @Test(timeout = 10000)
    fun testSelectRfc() = runBlocking {
        viewModel.refresh().join()

        val rfc = viewModel.uiState.value.rfcs.first()
        viewModel.selectRfc(rfc)
        assertEquals(rfc, viewModel.uiState.value.selectedRfc)

        viewModel.selectRfc(null)
        assertNull(viewModel.uiState.value.selectedRfc)
    }

    @Test(timeout = 10000)
    fun testApproveRfc_Success() = runBlocking {
        viewModel.refresh().join()

        viewModel.approveRfc("RFC-00142").join()

        val state = viewModel.uiState.value
        assertNull(state.votingInProgressId)
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice?.contains("RFC-00142") == true)
        assertTrue(state.userNotice?.contains("Approved") == true)
        assertNull(state.selectedRfc)
        assertNull(state.errorMessage)
    }

    @Test(timeout = 10000)
    fun testRejectRfc_Success() = runBlocking {
        viewModel.refresh().join()

        viewModel.rejectRfc("RFC-00143").join()

        val state = viewModel.uiState.value
        assertNull(state.votingInProgressId)
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice?.contains("RFC-00143") == true)
        assertTrue(state.userNotice?.contains("Rejected") == true)
        assertNull(state.selectedRfc)
        assertNull(state.errorMessage)
    }

    @Test(timeout = 10000)
    fun testApproveRfc_Failure_SetsErrorMessage() = runBlocking {
        viewModel.refresh().join()

        dispatcher.setResponse(
            "/api/rfcs/RFC-00142/vote",
            409,
            """{"detail": "RFC-00142 state conflict: concurrent vote already registered."}"""
        )

        viewModel.approveRfc("RFC-00142").join()

        val state = viewModel.uiState.value
        assertNull(state.votingInProgressId)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage?.contains("409") == true || state.errorMessage?.contains("conflict") == true)
    }

    @Test(timeout = 10000)
    fun testRejectRfc_Failure_SetsErrorMessage() = runBlocking {
        viewModel.refresh().join()

        dispatcher.setResponse(
            "/api/rfcs/RFC-00143/vote",
            400,
            """{"detail": "RFC-00143 is already in EXECUTED status. Further votes rejected."}"""
        )

        viewModel.rejectRfc("RFC-00143").join()

        val state = viewModel.uiState.value
        assertNull(state.votingInProgressId)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage?.contains("400") == true || state.errorMessage?.contains("EXECUTED") == true)
    }

    @Test(timeout = 10000)
    fun testClearNoticeAndDismissError() = runBlocking {
        viewModel.refresh().join()
        viewModel.approveRfc("RFC-00142").join()

        assertNotNull(viewModel.uiState.value.userNotice)
        viewModel.clearNotice()
        assertNull(viewModel.uiState.value.userNotice)

        dispatcher.setResponse("/api/rfcs/RFC-00142/vote", 500, """{"detail": "Server Error"}""")
        viewModel.approveRfc("RFC-00142").join()

        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.dismissError()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test(timeout = 10000)
    fun testRefreshFailure_SetsErrorMessageAndOffline() = runBlocking {
        dispatcher.setResponse("/api/rfcs", 500, """{"detail": "Internal Server Error"}""")

        viewModel.refresh().join()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isOffline)
        assertNotNull(state.errorMessage)
    }
}
