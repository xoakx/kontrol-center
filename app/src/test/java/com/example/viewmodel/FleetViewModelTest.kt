package com.example.viewmodel

import com.example.data.repository.FleetRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FleetViewModelTest : E2eTestHarness() {

    private lateinit var fleetRepository: FleetRepository
    private lateinit var viewModel: FleetViewModel

    @Before
    override fun setUp() {
        super.setUp()
        fleetRepository = FleetRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        viewModel = FleetViewModel(fleetRepository = fleetRepository, scope = CoroutineScope(Dispatchers.IO))
    }

    @Test(timeout = 10000)
    fun testInitialFleetLoad_12DaemonsDiscovered() = runBlocking {
        viewModel.refresh().join()

        val state = viewModel.uiState.value
        assertEquals(12, state.daemons.size)
        assertEquals(11, state.activeCount) // git-custodian is stopped
        assertEquals(12, state.totalCount)
        assertEquals("OPTIMAL", state.overallHealth)
    }

    @Test(timeout = 10000)
    fun testRestartDaemonAction_updatesNoticeAndTriggersRefresh() = runBlocking {
        viewModel.refresh().join()
        viewModel.restartDaemon("gemini-scribe.service").join()

        val state = viewModel.uiState.value
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice?.contains("gemini-scribe.service") == true)
        assertTrue(state.userNotice?.contains("restart") == true)
    }

    @Test(timeout = 10000)
    fun testStopDaemonAction_dispatchesStopMutation() = runBlocking {
        viewModel.refresh().join()
        viewModel.stopDaemon("transcriber.service").join()

        val state = viewModel.uiState.value
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice?.contains("stop") == true)
    }

    @Test(timeout = 10000)
    fun testDegradedFleetHealthEvaluation() = runBlocking {
        dispatcher.setResponse("/api/fleet/status", 200, MockTelemetryPayloads.degradedFleetDaemonsJson())
        viewModel.refresh().join()

        val state = viewModel.uiState.value
        assertEquals(1, state.failedCount)
        assertEquals("WARNING", state.overallHealth)
    }

    @Test(timeout = 10000)
    fun testClearNotice_resetsMessage() = runBlocking {
        viewModel.refresh().join()
        viewModel.restartDaemon("gemini-scribe.service").join()

        viewModel.clearNotice()
        assertEquals(null, viewModel.uiState.value.userNotice)
    }
}
