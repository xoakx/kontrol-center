package com.example.viewmodel

import com.example.data.repository.NetSecRepository
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetSecViewModelTest : E2eTestHarness() {

    private lateinit var repository: NetSecRepository
    private lateinit var viewModel: NetSecViewModel

    @Before
    override fun setUp() {
        super.setUp()
        repository = NetSecRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        viewModel = NetSecViewModel(repository = repository, scope = CoroutineScope(Dispatchers.IO))
    }

    @Test(timeout = 10000)
    fun testInitialLoad_success() = runBlocking {
        viewModel.refresh().join()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.overview)
        assertEquals(3, state.suricataAlerts.size)
        assertEquals(3, state.filteredAlerts.size)
        assertEquals(2, state.crowdSecDecisions.size)
        assertEquals(2, state.bouncers.size)
    }

    @Test(timeout = 10000)
    fun testStateTransitions_loadingToSuccess() = runBlocking {
        viewModel.refresh().join()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.overview)
        assertNull(state.errorMessage)
    }

    @Test(timeout = 10000)
    fun testStateTransitions_loadingToError() = runBlocking {
        dispatcher.setResponse("/api/netsec/overview", 500, """{"error": "Simulated 500 error"}""")

        viewModel.refresh().join()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage?.contains("NetSec telemetry refresh failed") == true)
    }

    @Test(timeout = 10000)
    fun testFilterAlerts_criticalOnly() = runBlocking {
        viewModel.refresh().join()

        viewModel.setSeverityFilter(1)

        val state = viewModel.uiState.value
        assertEquals(1, state.selectedSeverityFilter)
        assertEquals(1, state.filteredAlerts.size)
        assertEquals(1, state.filteredAlerts[0].alert.severity)
        assertEquals("ET EXPLOIT Remote Command Execution Attempt", state.filteredAlerts[0].alert.signature)
    }

    @Test(timeout = 10000)
    fun testFilterAlerts_resetToAll() = runBlocking {
        viewModel.refresh().join()

        viewModel.setSeverityFilter(1)
        assertEquals(1, viewModel.uiState.value.filteredAlerts.size)

        viewModel.setSeverityFilter(null)
        assertEquals(3, viewModel.uiState.value.filteredAlerts.size)
        assertNull(viewModel.uiState.value.selectedSeverityFilter)
    }

    @Test(timeout = 10000)
    fun testSearchAlerts_byIpAndSignature() = runBlocking {
        viewModel.refresh().join()

        viewModel.setSearchQuery("203.0.113.195")
        assertEquals(1, viewModel.uiState.value.filteredAlerts.size)
        assertEquals("203.0.113.195", viewModel.uiState.value.filteredAlerts[0].srcIp)

        viewModel.setSearchQuery("SYN Scan")
        assertEquals(1, viewModel.uiState.value.filteredAlerts.size)
        assertEquals("ET SCAN Nmap SYN Scan", viewModel.uiState.value.filteredAlerts[0].alert.signature)

        viewModel.setSearchQuery("")
        assertEquals(3, viewModel.uiState.value.filteredAlerts.size)
    }

    @Test(timeout = 10000)
    fun testCombinedFilter_severityAndSearch() = runBlocking {
        viewModel.refresh().join()

        // Severity = 2 AND query = "Scan" -> 1 match
        viewModel.setSeverityFilter(2)
        viewModel.setSearchQuery("Scan")
        assertEquals(1, viewModel.uiState.value.filteredAlerts.size)

        // Severity = 1 AND query = "Scan" -> 0 matches (strict logical AND)
        viewModel.setSeverityFilter(1)
        assertEquals(0, viewModel.uiState.value.filteredAlerts.size)
    }

    @Test(timeout = 10000)
    fun testUnbanIp_success_updatesNoticeAndEvicts() = runBlocking {
        viewModel.refresh().join()

        val ipToUnban = "209.99.190.113"
        viewModel.unbanIp(ipToUnban).join()

        val state = viewModel.uiState.value
        assertNull(state.unbanInProgressIp)
        assertNull(state.isUnbanningIp)
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice?.contains(ipToUnban) == true)
        assertTrue(state.crowdSecDecisions.none { it.value == ipToUnban })
    }

    @Test(timeout = 10000)
    fun testUnbanIp_inProgressState() = runBlocking {
        viewModel.refresh().join()

        val ipToUnban = "209.99.190.113"
        viewModel.unbanIp(ipToUnban).join()

        val state = viewModel.uiState.value
        assertNull(state.unbanInProgressIp)
        assertNull(state.isUnbanningIp)
    }

    @Test(timeout = 10000)
    fun testUnbanIp_failure_setsErrorMessage() = runBlocking {
        viewModel.refresh().join()

        // 1. Client-side invalid IP
        val invalidIp = "192.168.1.1; rm -rf /"
        viewModel.unbanIp(invalidIp).join()

        var state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage?.contains("Invalid IP format") == true)
        assertEquals(2, state.crowdSecDecisions.size)

        // 2. Server 500 error
        dispatcher.setResponse("/api/netsec/crowdsec/unban", 500, """{"error": "LAPI crash"}""")
        val validIp = "209.99.190.113"
        viewModel.unbanIp(validIp).join()

        state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage?.contains("Unban failed for $validIp") == true)
        assertEquals(2, state.crowdSecDecisions.size)
    }

    @Test(timeout = 10000)
    fun testOverallHealthCalculation() = runBlocking {
        viewModel.refresh().join()

        val state = viewModel.uiState.value
        // Initial state has 1 critical alert and 2 bans -> "CRITICAL"
        assertEquals("CRITICAL", state.overallHealth)

        // Static evaluation checks
        val crit = NetSecViewModel.evaluateOverallHealth(state.overview, state.suricataAlerts)
        assertEquals("CRITICAL", crit)

        val noCritAlerts = state.suricataAlerts.filter { it.alert.severity != 1 }
        val warn = NetSecViewModel.evaluateOverallHealth(state.overview, noCritAlerts)
        assertEquals("WARNING", warn)

        val optimalOverview = state.overview?.copy(crowdsecBanCount = 0)
        val optimal = NetSecViewModel.evaluateOverallHealth(optimalOverview, emptyList())
        assertEquals("OPTIMAL", optimal)
    }

    @Test(timeout = 10000)
    fun testClearNoticeAndClearError() = runBlocking {
        viewModel.refresh().join()

        viewModel.unbanIp("bad.ip.syntax").join()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.unbanIp("209.99.190.113").join()
        assertNotNull(viewModel.uiState.value.userNotice)

        viewModel.clearNotice()
        assertNull(viewModel.uiState.value.userNotice)
    }
}
