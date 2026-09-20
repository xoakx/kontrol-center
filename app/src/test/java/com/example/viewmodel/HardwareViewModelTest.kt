package com.example.viewmodel

import com.example.data.repository.SmartHomeRepository
import com.example.data.repository.TelemetryRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
class HardwareViewModelTest : E2eTestHarness() {

    private lateinit var telemetryRepository: TelemetryRepository
    private lateinit var smartHomeRepository: SmartHomeRepository
    private lateinit var viewModel: HardwareViewModel

    @Before
    override fun setUp() {
        super.setUp()
        telemetryRepository = TelemetryRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        smartHomeRepository = SmartHomeRepository(apiService = apiService, ioDispatcher = Dispatchers.IO)
        viewModel = HardwareViewModel(
            telemetryRepository = telemetryRepository,
            smartHomeRepository = smartHomeRepository,
            scope = CoroutineScope(Dispatchers.IO)
        )
    }

    @Test(timeout = 10000)
    fun testInitialHardwareLoad_populatesDualGpuAndCpuMetrics() = runBlocking {
        viewModel.refreshAll().join()

        val state = viewModel.uiState.value
        assertNotNull(state.telemetry)
        assertEquals(2, state.telemetry?.gpus?.size)
        assertEquals("performance", state.governor)
        assertEquals(41, state.peakGpuTemp)
        assertEquals(42.0, state.cpuTemp, 0.1)
        assertTrue(state.npuHealthy)

        // 20-core allocation (8 P-cores, 12 E-cores)
        assertEquals(20, state.perCoreLoads.size)
        assertEquals(8, state.pCoreLoads.size)
        assertEquals(12, state.eCoreLoads.size)
        assertTrue(state.pCoreAverageLoad > 0f)
        assertTrue(state.eCoreAverageLoad > 0f)

        assertFalse(state.isThermalSpike)
        assertFalse(state.isCriticalThermal)
    }

    @Test(timeout = 10000)
    fun testThermalSpikeEvaluation_flagsAlert() = runBlocking {
        dispatcher.setResponse("/api/telemetry", 200, MockTelemetryPayloads.thermalSpikeTelemetryJson())
        viewModel.refreshTelemetry().join()

        val state = viewModel.uiState.value
        assertEquals(89, state.peakGpuTemp)
        assertEquals(86.0, state.cpuTemp, 0.1)
        assertTrue(state.isThermalSpike)
        assertFalse(state.isCriticalThermal)
    }

    @Test(timeout = 10000)
    fun testCpuGovernorToggle_switchesGovernor() = runBlocking {
        viewModel.refreshAll().join()

        // Toggle to powersave
        viewModel.toggleCpuGovernor("powersave").join()

        var state = viewModel.uiState.value
        assertEquals("powersave", state.governor)
        assertNotNull(state.userNotice)

        // Toggle back to performance
        viewModel.toggleCpuGovernor("performance").join()

        state = viewModel.uiState.value
        assertEquals("performance", state.governor)
    }

    @Test(timeout = 10000)
    fun testAudioPipelineReanchor_dispatchesAndNotifies() = runBlocking {
        viewModel.refreshAll().join()
        viewModel.reanchorAudio().join()

        val state = viewModel.uiState.value
        assertFalse(state.isAudioReanchoring)
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice?.contains("Audio pipeline re-anchored") == true)
    }

    @Test(timeout = 10000)
    fun testSmartHomeSensorsLoad_populatesDevices() = runBlocking {
        viewModel.refreshAll().join()

        val state = viewModel.uiState.value
        assertNotNull(state.smartHome)

        val apollo = state.smartHome?.sensors?.get("apollo_msr2")
        assertNotNull(apollo)
        assertTrue(apollo?.presence == true)

        val xmos = state.smartHome?.voiceSatellites?.get("xvf3800")
        assertNotNull(xmos)
        assertEquals("listening", xmos?.state)

        val door = state.smartHome?.zigbeePerimeter?.get("sonoff_door")
        assertNotNull(door)
        assertEquals("closed", door?.state)

        val purifier = state.smartHome?.airPurifier?.get("levoit_purifier")
        assertNotNull(purifier)
        assertEquals(2, purifier?.fanSpeed)
    }

    @Test(timeout = 10000)
    fun testPurifierControls_setSpeedAndTogglePower() = runBlocking {
        viewModel.refreshAll().join()

        viewModel.setPurifierFanSpeed(3).join()
        assertNotNull(viewModel.uiState.value.userNotice)

        viewModel.togglePurifierPower().join()
        assertNotNull(viewModel.uiState.value.userNotice)
    }
}
