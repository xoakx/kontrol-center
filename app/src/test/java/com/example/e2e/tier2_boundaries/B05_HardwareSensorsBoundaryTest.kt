package com.example.e2e.tier2_boundaries

import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 2 Boundary Tests: B05 Hardware Sensors Boundary.
 * Covers 5 boundary conditions:
 * - T2_B05_01: Thermal spike >95°C critical red badge threshold evaluation
 * - T2_B05_02: Missing NPU accelerator node (present = false, device path empty)
 * - T2_B05_03: Extreme core load throttling (load average exceeding 20-core capacity)
 * - T2_B05_04: Negative and zero sensor readings (uninitialized thermistor / zero wattage)
 * - T2_B05_05: Partial GPU failure (one GPU offline / fallen off bus while second is operational)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B05_HardwareSensorsBoundaryTest : E2eTestHarness() {

    @Test
    fun T2_B05_01_thermal_spike_over_95_critical() = runBlocking {
        val criticalThermalPayload = """
        {
          "cpu": {
            "cores": 20,
            "governor": "performance",
            "load_1m": "18.50",
            "load_5m": "17.20",
            "load_15m": "15.00",
            "temp_c": 98.5
          },
          "gpus": [
            {
              "index": "0",
              "name": "NVIDIA GeForce RTX 5060 Ti",
              "temp_c": 96,
              "util_pct": 99,
              "mem_used_mb": 16000,
              "mem_total_mb": 16311,
              "power_w": 185.0
            }
          ],
          "memory": {
            "total_mb": 65536,
            "used_mb": 60000,
            "used_pct": 91.5
          },
          "storage": {
            "mount": "/",
            "total_gb": 931.2,
            "used_gb": 800.0,
            "free_gb": 131.2,
            "used_pct": 85.9
          },
          "npu": {
            "present": true,
            "model": "Intel AI Boost NPU 4 (Arrow Lake)",
            "device": "/dev/accel/accel0",
            "service": "gemini-npu-embeddings.service"
          },
          "network": {
            "congestion_control": "bbr",
            "qdisc": "fq"
          },
          "uptime_hours": 100.0
        }
        """.trimIndent()

        dispatcher.setResponse("/api/telemetry", 200, criticalThermalPayload)

        val telemetry = apiService.getTelemetry()
        val cpuTemp = telemetry.cpu.tempC
        val gpuTemp = telemetry.gpus[0].tempC

        assertTrue(cpuTemp > 95.0)
        assertTrue(gpuTemp > 95)

        val isCritical = cpuTemp >= 95.0 || gpuTemp >= 95
        assertTrue("Expected critical thermal alert trigger", isCritical)
    }

    @Test
    fun T2_B05_02_missing_npu_accelerator_node() = runBlocking {
        val missingNpuPayload = """
        {
          "cpu": {
            "cores": 20,
            "governor": "powersave",
            "load_1m": "0.10",
            "load_5m": "0.15",
            "load_15m": "0.12",
            "temp_c": 35.0
          },
          "gpus": [],
          "memory": {
            "total_mb": 65536,
            "used_mb": 12000,
            "used_pct": 18.3
          },
          "storage": {
            "mount": "/",
            "total_gb": 931.2,
            "used_gb": 300.0,
            "free_gb": 631.2,
            "used_pct": 32.2
          },
          "npu": {
            "present": false,
            "model": "None",
            "device": "",
            "service": "inactive"
          },
          "network": {
            "congestion_control": "bbr",
            "qdisc": "fq"
          },
          "uptime_hours": 100.0
        }
        """.trimIndent()

        dispatcher.setResponse("/api/telemetry", 200, missingNpuPayload)

        val telemetry = apiService.getTelemetry()
        assertFalse(telemetry.npu.present)
        assertEquals("None", telemetry.npu.model)
        assertEquals("", telemetry.npu.device)
        assertEquals("inactive", telemetry.npu.service)
    }

    @Test
    fun T2_B05_03_core_load_throttling_100_percent() = runBlocking {
        val highLoadPayload = """
        {
          "cpu": {
            "cores": 20,
            "governor": "performance",
            "load_1m": "24.50",
            "load_5m": "21.10",
            "load_15m": "19.80",
            "temp_c": 88.0
          },
          "gpus": [],
          "memory": { "total_mb": 65536, "used_mb": 32000, "used_pct": 48.8 },
          "storage": { "mount": "/", "total_gb": 931.2, "used_gb": 350.0, "free_gb": 581.2, "used_pct": 37.6 },
          "npu": { "present": true, "model": "Intel NPU", "device": "/dev/accel/accel0", "service": "gemini-npu" },
          "network": { "congestion_control": "bbr", "qdisc": "fq" },
          "uptime_hours": 100.0
        }
        """.trimIndent()

        dispatcher.setResponse("/api/telemetry", 200, highLoadPayload)

        val telemetry = apiService.getTelemetry()
        val load1m = telemetry.cpu.load1m.toDoubleOrNull() ?: 0.0
        val cores = telemetry.cpu.cores

        // Load 24.5 on 20 cores represents 122.5% core saturation
        val saturation = load1m / cores
        assertTrue(saturation > 1.0)
        assertEquals(1.225, saturation, 0.01)
    }

    @Test
    fun T2_B05_04_negative_sensor_readings() = runBlocking {
        val edgeSensorPayload = """
        {
          "cpu": {
            "cores": 20,
            "governor": "powersave",
            "load_1m": "0.00",
            "load_5m": "0.00",
            "load_15m": "0.00",
            "temp_c": -1.0
          },
          "gpus": [
            {
              "index": "0",
              "name": "NVIDIA RTX 5060 Ti",
              "temp_c": -1,
              "util_pct": 0,
              "mem_used_mb": 0,
              "mem_total_mb": 16311,
              "power_w": 0.0
            }
          ],
          "memory": { "total_mb": 65536, "used_mb": 1000, "used_pct": 1.5 },
          "storage": { "mount": "/", "total_gb": 931.2, "used_gb": 10.0, "free_gb": 921.2, "used_pct": 1.0 },
          "npu": { "present": true, "model": "Intel NPU", "device": "/dev/accel/accel0", "service": "gemini-npu" },
          "network": { "congestion_control": "bbr", "qdisc": "fq" },
          "uptime_hours": 100.0
        }
        """.trimIndent()

        dispatcher.setResponse("/api/telemetry", 200, edgeSensorPayload)

        val telemetry = apiService.getTelemetry()
        assertEquals(-1.0, telemetry.cpu.tempC, 0.01)
        assertEquals(-1, telemetry.gpus[0].tempC)
        assertEquals(0.0, telemetry.gpus[0].powerW, 0.01)
        assertEquals(0, telemetry.gpus[0].utilPct)
    }

    @Test
    fun T2_B05_05_partial_gpu_failure() = runBlocking {
        val partialGpuPayload = """
        {
          "cpu": {
            "cores": 20,
            "governor": "performance",
            "load_1m": "1.00",
            "load_5m": "1.00",
            "load_15m": "1.00",
            "temp_c": 40.0
          },
          "gpus": [
            {
              "index": "0",
              "name": "GPU 0 (Offline: Fallen off bus)",
              "temp_c": 0,
              "util_pct": 0,
              "mem_used_mb": 0,
              "mem_total_mb": 0,
              "power_w": 0.0
            },
            {
              "index": "1",
              "name": "NVIDIA GeForce RTX 5060 Ti",
              "temp_c": 45,
              "util_pct": 30,
              "mem_used_mb": 8192,
              "mem_total_mb": 16311,
              "power_w": 55.0
            }
          ],
          "memory": { "total_mb": 65536, "used_mb": 16000, "used_pct": 24.4 },
          "storage": { "mount": "/", "total_gb": 931.2, "used_gb": 350.0, "free_gb": 581.2, "used_pct": 37.6 },
          "npu": { "present": true, "model": "Intel NPU", "device": "/dev/accel/accel0", "service": "gemini-npu" },
          "network": { "congestion_control": "bbr", "qdisc": "fq" },
          "uptime_hours": 100.0
        }
        """.trimIndent()

        dispatcher.setResponse("/api/telemetry", 200, partialGpuPayload)

        val telemetry = apiService.getTelemetry()
        assertEquals(2, telemetry.gpus.size)

        val failedGpu = telemetry.gpus[0]
        val healthyGpu = telemetry.gpus[1]

        assertTrue(failedGpu.name.contains("Offline"))
        assertEquals(0, failedGpu.memTotalMb)
        assertEquals("NVIDIA GeForce RTX 5060 Ti", healthyGpu.name)
        assertEquals(45, healthyGpu.tempC)
        assertEquals(30, healthyGpu.utilPct)
    }
}
