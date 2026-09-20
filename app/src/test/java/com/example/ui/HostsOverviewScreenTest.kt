package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.entity.HostEntity
import com.example.data.repository.FleetRepository
import com.example.data.repository.NetSecRepository
import com.example.data.repository.TelemetryRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.ui.screens.ExecutiveHealthCard
import com.example.ui.screens.HostsOverviewScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.OverviewViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostsOverviewScreenTest : E2eTestHarness() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mainViewModel: MainViewModel
    private lateinit var overviewViewModel: OverviewViewModel

    @Before
    override fun setUp() {
        super.setUp()

        mainViewModel = MainViewModel(context as android.app.Application)

        val telemetryRepo = TelemetryRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        val fleetRepo = FleetRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        val netsecRepo = NetSecRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)

        overviewViewModel = OverviewViewModel(
            telemetryRepository = telemetryRepo,
            fleetRepository = fleetRepo,
            netSecRepository = netsecRepo,
            hostRepository = hostRepository,
            scope = CoroutineScope(Dispatchers.IO),
            ioDispatcher = Dispatchers.IO
        )
    }

    @Test
    fun testExecutiveOverviewCards_allDisplayed(): Unit = runBlocking {
        val host = HostEntity(
            name = "Workstation fml",
            address = "100.111.123.93",
            tailscaleAddress = "100.111.123.93",
            lanAddress = "192.168.1.161",
            lastLatencyMs = 14L,
            activeEndpoint = "http://100.111.123.93:8899",
            isOnline = true
        )
        hostRepository.insertHost(host)
        overviewViewModel.setActiveHost(host)
        overviewViewModel.refresh().join()

        composeTestRule.setContent {
            MyApplicationTheme {
                HostsOverviewScreen(
                    viewModel = mainViewModel,
                    overviewViewModel = overviewViewModel
                )
            }
        }

        // Verify the 4 primary overview cards exist
        composeTestRule.onNodeWithTag("card_system_health").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_fleet_daemons").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_security_siem").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_connected_workstations").assertIsDisplayed()
        composeTestRule.onNodeWithTag("overall_health_badge").assertIsDisplayed()
    }

    @Test
    fun testExecutiveOverviewCards_drillDownNavigation(): Unit = runBlocking {
        var hardwareNavigated = false
        var fleetNavigated = false
        var netsecNavigated = false

        composeTestRule.setContent {
            MyApplicationTheme {
                HostsOverviewScreen(
                    viewModel = mainViewModel,
                    overviewViewModel = overviewViewModel,
                    onNavigateToHardware = { hardwareNavigated = true },
                    onNavigateToFleet = { fleetNavigated = true },
                    onNavigateToNetSec = { netsecNavigated = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("card_system_health").performClick()
        assertTrue("Tapping System Health card must trigger hardware navigation", hardwareNavigated)

        composeTestRule.onNodeWithTag("card_fleet_daemons").performClick()
        assertTrue("Tapping Fleet Daemons card must trigger fleet navigation", fleetNavigated)

        composeTestRule.onNodeWithTag("card_security_siem").performClick()
        assertTrue("Tapping Security & SIEM card must trigger netsec navigation", netsecNavigated)
    }
}
