package com.example.e2e.tier1_features

import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tier 1 Tests for Feature 6: Smart Home Telemetry Integration.
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F06_01: Apollo MSR-2 mmWave presence radar and CO2 environmental metrics
 * - T1_F06_02: XMOS XVF3800 voice satellite DSP beam tracking and wake intents
 * - T1_F06_03: Sonoff Zigbee perimeter door contact binary state and battery
 * - T1_F06_04: Levoit Core 400S smart purifier air quality index and filter life
 * - T1_F06_05: Smart home mutation control dispatch verification
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F06_SmartHomeTest : E2eTestHarness() {

    @Test
    fun T1_F06_01_apollo_msr2_radar_sensor_parsing() = runBlocking {
        val smarthome = apiService.getSmartHome()
        assertNotNull(smarthome)

        val apollo = smarthome.sensors["apollo_msr2"]
        assertNotNull(apollo)
        assertTrue(apollo?.presence == true)
        assertEquals(1.45, apollo?.targetDistanceM ?: 0.0, 0.01)
        assertEquals(35, apollo?.movementEnergy)
        assertEquals(42, apollo?.stillEnergy)
        assertEquals(120.5, apollo?.illuminanceLux ?: 0.0, 0.1)
        assertEquals(640, apollo?.co2Ppm)
    }

    @Test
    fun T1_F06_02_xmos_xvf3800_voice_satellite_tracking() = runBlocking {
        val smarthome = apiService.getSmartHome()
        val satellite = smarthome.voiceSatellites["xvf3800"]
        assertNotNull(satellite)
        assertEquals("listening", satellite?.state)
        assertEquals(185, satellite?.beamAngleDeg)
        assertEquals("Okay Nabu", satellite?.wakeWord)
        assertEquals("Living Room", satellite?.location)
        assertEquals("Voice assistant wake ping verified", satellite?.lastIntent)
    }

    @Test
    fun T1_F06_03_sonoff_door_contact_sensor_state() = runBlocking {
        val smarthome = apiService.getSmartHome()
        val door = smarthome.zigbeePerimeter["sonoff_door"]
        assertNotNull(door)
        assertEquals("closed", door?.state)
        assertEquals(95, door?.batteryPct)
        assertEquals(140, door?.lqi)
    }

    @Test
    fun T1_F06_04_levoit_core_400s_purifier_telemetry() = runBlocking {
        val smarthome = apiService.getSmartHome()
        val purifier = smarthome.airPurifier["levoit_purifier"]
        assertNotNull(purifier)
        assertEquals("on", purifier?.power)
        assertEquals("auto", purifier?.mode)
        assertEquals(2, purifier?.fanSpeed)
        assertEquals(4, purifier?.pm25Aqi)
        assertEquals("Excellent", purifier?.airQuality)
        assertEquals(88, purifier?.filterLifePct)
    }

    @Test
    fun T1_F06_05_smarthome_control_dispatch() = runBlocking {
        val controlPayload = """
        {
          "category": "air_purifier",
          "id": "levoit_purifier",
          "action": "set_fan_speed",
          "value": 3
        }
        """.trimIndent()

        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/smarthome/control")
            .post(controlPayload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val body = response.body?.string()
            assertNotNull(body)
            assertTrue(body?.contains(""""success":true""") == true || body?.contains(""""success": true""") == true)

            val lastReq = dispatcher.lastRecordedRequest
            assertNotNull(lastReq)
            assertEquals("/api/smarthome/control", lastReq?.url?.encodedPath)
            val interceptedBody = dispatcher.extractRequestBody(lastReq!!)
            assertTrue(interceptedBody.contains("set_fan_speed"))
        }
    }
}
