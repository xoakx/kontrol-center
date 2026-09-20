package com.example.repository

import com.example.data.repository.TelemetryRepository
import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
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
class TelemetryRepositoryTest : E2eTestHarness() {

    private lateinit var telemetryRepository: TelemetryRepository

    @Before
    override fun setUp() {
        super.setUp()
        telemetryRepository = TelemetryRepository(apiService = apiService)
    }

    @Test
    fun testGetTelemetry_dualRtx5060TiMetrics() = runBlocking {
        val result = telemetryRepository.getTelemetry()
        assertTrue(result.isSuccess)
        val telem = result.getOrNull()
        assertNotNull(telem)

        // Dual GPU validation
        assertEquals(2, telem?.gpus?.size)
        val gpu0 = telem?.gpus?.get(0)
        val gpu1 = telem?.gpus?.get(1)

        assertEquals("0", gpu0?.index)
        assertEquals(41, gpu0?.tempC)
        assertEquals(8, gpu0?.utilPct)
        assertEquals(6442, gpu0?.memUsedMb)
        assertEquals(16311, gpu0?.memTotalMb)
        assertEquals(14.2, gpu0?.powerW ?: 0.0, 0.1)

        assertEquals("1", gpu1?.index)
        assertEquals(27, gpu1?.tempC)
        assertEquals(10073, gpu1?.memUsedMb)
    }

    @Test
    fun testGetTelemetry_intelUltra7CpuMetrics() = runBlocking {
        val result = telemetryRepository.getTelemetry()
        val cpu = result.getOrNull()?.cpu
        assertNotNull(cpu)

        assertEquals(20, cpu?.cores)
        assertEquals("performance", cpu?.governor)
        assertEquals(42.0, cpu?.tempC ?: 0.0, 0.1)
        assertEquals("0.45", cpu?.load1m)
    }

    @Test
    fun testGetTelemetry_openVinoNpuHealth() = runBlocking {
        val result = telemetryRepository.getTelemetry()
        val npu = result.getOrNull()?.npu
        assertNotNull(npu)

        assertTrue(npu?.present == true)
        assertEquals("Intel AI Boost NPU 4 (Arrow Lake)", npu?.model)
        assertEquals("/dev/accel/accel0", npu?.device)
        assertEquals("gemini-npu-embeddings.service", npu?.service)
    }

    @Test
    fun testGetTelemetry_memoryAndStorage() = runBlocking {
        val result = telemetryRepository.getTelemetry()
        val mem = result.getOrNull()?.memory
        val storage = result.getOrNull()?.storage

        assertEquals(65536L, mem?.totalMb)
        assertEquals(18432L, mem?.usedMb)
        assertEquals(28.1, mem?.usedPct ?: 0.0, 0.1)

        assertEquals(931.2, storage?.totalGb ?: 0.0, 0.1)
        assertEquals(361.9, storage?.usedGb ?: 0.0, 0.1)
        assertEquals("/", storage?.mount)
    }

    @Test
    fun testThermalThresholdEvaluation() = runBlocking {
        // Normal telemetry
        telemetryRepository.getTelemetry()
        val normal = telemetryRepository.telemetry.value
        assertFalse(TelemetryRepository.isThermalSpike(normal))
        assertFalse(TelemetryRepository.isCriticalThermal(normal))

        // Thermal Spike (GPU 89°C, CPU 86°C)
        dispatcher.setResponse("/api/telemetry", 200, MockTelemetryPayloads.thermalSpikeTelemetryJson())
        telemetryRepository.getTelemetry()
        val spike = telemetryRepository.telemetry.value

        assertTrue(TelemetryRepository.isThermalSpike(spike))
        assertFalse(TelemetryRepository.isCriticalThermal(spike))
    }

    @Test
    fun testSetCpuGovernor_perfLockDispatch() = runBlocking {
        val result = telemetryRepository.setCpuGovernor("performance")
        assertTrue(result.isSuccess)
        val resp = result.getOrNull()
        assertNotNull(resp)
        assertTrue(resp?.success == true)
        assertEquals("perf_lock", resp?.action)
        assertTrue(resp?.message?.contains("throughput-performance") == true)
    }

    @Test
    fun testReanchorAudioPipeline_dispatchAction() = runBlocking {
        val result = telemetryRepository.reanchorAudioPipeline()
        assertTrue(result.isSuccess)
        val resp = result.getOrNull()
        assertNotNull(resp)
        assertTrue(resp?.success == true)
        assertEquals("audio_reanchor", resp?.action)
        assertTrue(resp?.message?.contains("Audio pipeline re-anchored") == true)
    }

    @Test
    fun testDiagnosticDispatchActions_sreSweepAndGitSync() = runBlocking {
        val sreResult = telemetryRepository.triggerSreSweep()
        assertTrue(sreResult.isSuccess)
        assertTrue(sreResult.getOrNull()?.message?.contains("SRE Scan Complete") == true)

        val gitResult = telemetryRepository.triggerGitSync()
        assertTrue(gitResult.isSuccess)
        assertTrue(gitResult.getOrNull()?.message?.contains("Git Audit") == true)
    }
}
