package com.example.e2e.tier1_features

import com.example.e2e.harness.E2eTestHarness
import com.example.e2e.harness.MockTelemetryPayloads
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1 Tests for Feature 5: Hardware Telemetry (Dual GPU, 20C CPU, NPU).
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F05_01: Dual RTX 5060 Ti GPU metrics (temperatures, VRAM, power draw)
 * - T1_F05_02: Intel Ultra 7 265K 20-core topology and load averages
 * - T1_F05_03: OpenVINO Intel AI Boost NPU accelerator health and node path
 * - T1_F05_04: RAM memory and NVMe filesystem storage utilization
 * - T1_F05_05: Silicon thermal threshold evaluation (Normal vs Thermal Spike)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F05_HardwareSensorsTest : E2eTestHarness() {

    @Test
    fun T1_F05_01_dual_rtx_5060_ti_metrics() = runBlocking {
        val telemetry = apiService.getTelemetry()
        assertNotNull(telemetry)
        assertEquals(2, telemetry.gpus.size)

        val gpu0 = telemetry.gpus[0]
        assertEquals("0", gpu0.index)
        assertEquals("NVIDIA GeForce RTX 5060 Ti", gpu0.name)
        assertEquals(41, gpu0.tempC)
        assertEquals(8, gpu0.utilPct)
        assertEquals(6442, gpu0.memUsedMb)
        assertEquals(16311, gpu0.memTotalMb)
        assertEquals(14.2, gpu0.powerW, 0.1)

        val gpu1 = telemetry.gpus[1]
        assertEquals("1", gpu1.index)
        assertEquals("NVIDIA GeForce RTX 5060 Ti", gpu1.name)
        assertEquals(27, gpu1.tempC)
        assertEquals(0, gpu1.utilPct)
        assertEquals(10073, gpu1.memUsedMb)
        assertEquals(16311, gpu1.memTotalMb)
        assertEquals(4.1, gpu1.powerW, 0.1)
    }

    @Test
    fun T1_F05_02_intel_ultra_7_265k_loads() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val cpu = telemetry.cpu
        assertEquals("0.45", cpu.load1m)
        assertEquals("0.52", cpu.load5m)
        assertEquals("0.48", cpu.load15m)
        assertEquals("performance", cpu.governor)
        assertEquals(42.0, cpu.tempC, 0.1)
        assertEquals(20, cpu.cores) // 8 P-cores + 12 E-cores
    }

    @Test
    fun T1_F05_03_intel_npu_ai_boost_telemetry() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val npu = telemetry.npu
        assertTrue(npu.present)
        assertEquals("Intel AI Boost NPU 4 (Arrow Lake)", npu.model)
        assertEquals("/dev/accel/accel0", npu.device)
        assertEquals("gemini-npu-embeddings.service", npu.service)
    }

    @Test
    fun T1_F05_04_ram_and_nvme_storage_breakdown() = runBlocking {
        val telemetry = apiService.getTelemetry()
        val mem = telemetry.memory
        assertEquals(65536L, mem.totalMb)
        assertEquals(18432L, mem.usedMb)
        assertEquals(28.1, mem.usedPct, 0.1)

        val storage = telemetry.storage
        assertEquals(931.2, storage.totalGb, 0.1)
        assertEquals(361.9, storage.usedGb, 0.1)
        assertEquals(565.3, storage.freeGb, 0.1)
        assertEquals(38.9, storage.usedPct, 0.1)
        assertEquals("/", storage.mount)
    }

    @Test
    fun T1_F05_05_silicon_thermal_threshold_badging() = runBlocking {
        // 1. Normal state check
        val normalTelemetry = apiService.getTelemetry()
        assertTrue(normalTelemetry.gpus[0].tempC < 80)
        assertTrue(normalTelemetry.cpu.tempC < 80.0)

        // 2. Thermal Spike state check
        dispatcher.setResponse("/api/telemetry", 200, MockTelemetryPayloads.thermalSpikeTelemetryJson())
        val spikeTelemetry = apiService.getTelemetry()

        val peakGpuTemp = spikeTelemetry.gpus.maxOf { it.tempC }
        val cpuTemp = spikeTelemetry.cpu.tempC

        assertEquals(89, peakGpuTemp)
        assertEquals(86.0, cpuTemp, 0.1)

        // Threshold logic: >= 85°C triggers CRITICAL status
        val isCriticalThermal = peakGpuTemp >= 85 || cpuTemp >= 85.0
        assertTrue(isCriticalThermal)
    }
}
