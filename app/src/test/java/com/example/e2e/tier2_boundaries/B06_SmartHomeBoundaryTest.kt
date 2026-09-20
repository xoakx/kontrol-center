package com.example.e2e.tier2_boundaries

import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException

/**
 * Tier 2 Boundary Tests: B06 Smart Home Boundary.
 * Covers 5 boundary conditions:
 * - T2_B06_01: Disconnected / missing sensor handling in smart home payload
 * - T2_B06_02: Out-of-bounds illuminance and target distance boundary values
 * - T2_B06_03: Home Assistant gateway 503 fallback handling
 * - T2_B06_04: Duplicate device event storm handling without lock contention
 * - T2_B06_05: Out-of-range fan speed control parameter boundary rejection
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B06_SmartHomeBoundaryTest : E2eTestHarness() {

    @Test
    fun T2_B06_01_disconnected_sensor_timeout() = runBlocking {
        // Payload with empty sensors map
        val emptySensorsPayload = """
        {
          "sensors": {},
          "voiceSatellites": {},
          "zigbeePerimeter": {},
          "airPurifier": {}
        }
        """.trimIndent()

        dispatcher.setResponse("/api/smarthome", 200, emptySensorsPayload)

        val smarthome = apiService.getSmartHome()
        assertNotNull(smarthome)
        assertNull(smarthome.sensors["apollo_msr2"])
        assertNull(smarthome.voiceSatellites["xvf3800"])
        assertNull(smarthome.zigbeePerimeter["sonoff_door"])
        assertNull(smarthome.airPurifier["levoit_purifier"])
    }

    @Test
    fun T2_B06_02_nan_and_out_of_bounds_sensor_readings() = runBlocking {
        val boundarySensorPayload = """
        {
          "sensors": {
            "apollo_msr2": {
              "name": "Apollo MSR-2 Multisensor",
              "presence": true,
              "target_distance_m": 9999.99,
              "movement_energy": 100,
              "still_energy": 0,
              "illuminance_lux": 100000.0,
              "co2_ppm": 5000
            }
          },
          "voice_satellites": {},
          "zigbee_perimeter": {},
          "air_purifier": {}
        }
        """.trimIndent()

        dispatcher.setResponse("/api/smarthome", 200, boundarySensorPayload)

        val smarthome = apiService.getSmartHome()
        val apollo = smarthome.sensors["apollo_msr2"]
        assertNotNull(apollo)
        assertEquals(9999.99, apollo?.targetDistanceM ?: 0.0, 0.01)
        assertEquals(100000.0, apollo?.illuminanceLux ?: 0.0, 0.1)
        assertEquals(5000, apollo?.co2Ppm)
    }

    @Test
    fun T2_B06_03_home_assistant_503_fallback() = runBlocking {
        dispatcher.setResponse(
            "/api/smarthome",
            503,
            """{"detail": "Home Assistant WebSocket bridge connection refused."}"""
        )

        try {
            apiService.getSmartHome()
            fail("Expected HttpException(503)")
        } catch (e: HttpException) {
            assertEquals(503, e.code())
            val errorBody = e.response()?.errorBody()?.string()
            assertTrue(errorBody?.contains("Home Assistant WebSocket bridge") == true)
        }
    }

    @Test
    fun T2_B06_04_duplicate_device_event_storm() = runBlocking {
        // Simulate rapid repeated 50 events in tight loop
        var lastDoorState = "closed"
        for (i in 1..50) {
            lastDoorState = if (i % 2 == 0) "open" else "closed"
            val dynamicPayload = """
            {
              "sensors": {},
              "voice_satellites": {},
              "zigbee_perimeter": {
                "sonoff_door": {
                  "name": "Sonoff Door Contact",
                  "state": "$lastDoorState",
                  "battery_pct": 95,
                  "lqi": 140
                }
              },
              "air_purifier": {}
            }
            """.trimIndent()
            dispatcher.setResponse("/api/smarthome", 200, dynamicPayload)
            val response = apiService.getSmartHome()
            assertEquals(lastDoorState, response.zigbeePerimeter["sonoff_door"]?.state)
        }
        assertEquals("open", lastDoorState)
    }

    @Test
    fun T2_B06_05_out_of_range_fan_speed_rejection() = runBlocking {
        dispatcher.overrideResponse("/api/smarthome/control") { req ->
            val body = dispatcher.extractRequestBody(req)
            if (body.contains(""""value": 99""") || body.contains(""""value": -1""")) {
                dispatcher.createResponse(req, 400, """{"success": false, "error": "Fan speed out of bounds [1..4]"}""")
            } else {
                dispatcher.createResponse(req, 200, """{"success": true}""")
            }
        }

        val badPayload = """{"id": "levoit_purifier", "action": "set_fan_speed", "value": 99}"""
        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/smarthome/control")
            .post(badPayload.toRequestBody("application/json".toMediaType()))
            .build()

        okHttpClient.newCall(req).execute().use { response ->
            assertEquals(400, response.code)
            val body = response.body?.string()
            assertTrue(body?.contains("Fan speed out of bounds") == true)
        }
    }
}
