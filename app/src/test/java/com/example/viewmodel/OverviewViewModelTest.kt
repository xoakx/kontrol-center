package com.example.viewmodel

import com.example.data.entity.HostEntity
import com.example.data.repository.FleetRepository
import com.example.data.repository.NetSecRepository
import com.example.data.repository.TelemetryRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.service.ConnectionStatus
import com.example.service.NetworkMeshManagerImpl
import com.example.service.ProbeResult
import com.example.service.SocketProber
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
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverviewViewModelTest : E2eTestHarness() {

    private lateinit var telemetryRepository: TelemetryRepository
    private lateinit var fleetRepository: FleetRepository
    private lateinit var netSecRepository: NetSecRepository
    private lateinit var meshManager: NetworkMeshManagerImpl
    private lateinit var viewModel: OverviewViewModel

    @Before
    override fun setUp() {
        super.setUp()

        telemetryRepository = TelemetryRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        fleetRepository = FleetRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        netSecRepository = NetSecRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)

        val fakeProber = SocketProber { ip, _, _ ->
            if (ip == "100.111.123.93") ProbeResult(true, 14L) else ProbeResult(false, -1L)
        }
        meshManager = NetworkMeshManagerImpl(
            scope = CoroutineScope(Dispatchers.IO),
            ioDispatcher = Dispatchers.IO,
            socketProber = fakeProber,
            probeTimeoutMs = 1500
        )

        viewModel = OverviewViewModel(
            telemetryRepository = telemetryRepository,
            fleetRepository = fleetRepository,
            netSecRepository = netSecRepository,
            hostRepository = hostRepository,
            networkMeshManager = meshManager,
            scope = CoroutineScope(Dispatchers.IO),
            ioDispatcher = Dispatchers.IO
        )
    }

    @Test
    fun testInitialLoad_synthesizesOverviewSummary() = runBlocking {
        val host = HostEntity(
            name = "fml",
            address = "100.111.123.93",
            tailscaleAddress = "100.111.123.93",
            lanAddress = "192.168.1.161",
            lastLatencyMs = 14L,
            activeEndpoint = "http://100.111.123.93:8899",
            isOnline = true
        )
        hostRepository.insertHost(host)
        viewModel.setActiveHost(host)

        viewModel.refresh().join()

        val state = viewModel.uiState.value
        val summary = state.summary

        assertNotNull(summary)
        assertTrue(summary.systemHealthCard.contains("41°C"))
        assertTrue(summary.systemHealthCard.contains("RAM: 28%"))
        assertEquals("11/12 Active", summary.fleetDaemonsCard)
        assertEquals("2 Active Bans • 5 Alerts", summary.securitySiemCard)
        assertTrue(summary.connectedWorkstationsCard.contains("Connected via Tailscale"))
        assertTrue(summary.connectedWorkstationsCard.contains("14ms"))
        assertEquals("WARNING", summary.overallStatus)
    }

    @Test
    fun testOfflineSubsystem_gracefulHandling() {
        val summary = OverviewViewModel.aggregateDashboard(null, null, null, null)

        assertEquals("System Health Unavailable", summary.systemHealthCard)
        assertEquals("Fleet Offline", summary.fleetDaemonsCard)
        assertEquals("Security Telemetry Offline", summary.securitySiemCard)
        assertEquals("Offline", summary.connectedWorkstationsCard)
        assertEquals("OFFLINE", summary.overallStatus)
    }

    @Test
    fun testSub5SecondReadabilityLatencyBenchmark() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val fleet = apiService.getFleetStatus()
        val netsec = apiService.getNetSecOverview()
        val host = HostEntity(name = "fml", address = "100.111.123.93", lastLatencyMs = 12L)

        val elapsedMs = measureTimeMillis {
            repeat(100) {
                OverviewViewModel.aggregateDashboard(telemetry, fleet, netsec, host)
            }
        }

        assertTrue("100 aggregations took ${elapsedMs}ms, well within SLA", elapsedMs < 500)
    }

    @Test
    fun testExtremeLatencyFormatting() {
        val host = HostEntity(name = "fml", address = "100.111.123.93", isOnline = true, lastLatencyMs = 12450L)
        val summary = OverviewViewModel.aggregateDashboard(null, null, null, host)
        assertTrue(summary.connectedWorkstationsCard.contains("12450ms"))
    }
}
