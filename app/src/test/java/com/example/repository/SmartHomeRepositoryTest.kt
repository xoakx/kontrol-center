package com.example.repository

import com.example.data.repository.SmartHomeRepository
import com.example.e2e.harness.E2eTestHarness
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
class SmartHomeRepositoryTest : E2eTestHarness() {

    private lateinit var smartHomeRepository: SmartHomeRepository

    @Before
    override fun setUp() {
        super.setUp()
        smartHomeRepository = SmartHomeRepository(apiService = apiService)
    }

    @Test
    fun testGetSmartHome_apolloMsr2RadarMetrics() = runBlocking {
        val result = smartHomeRepository.getSmartHome()
        assertTrue(result.isSuccess)
        val sh = result.getOrNull()
        assertNotNull(sh)

        val apollo = sh?.sensors?.get("apollo_msr2")
        assertNotNull(apollo)
        assertTrue(apollo?.presence == true)
        assertEquals(1.45, apollo?.targetDistanceM ?: 0.0, 0.01)
        assertEquals(35, apollo?.movementEnergy)
        assertEquals(42, apollo?.stillEnergy)
        assertEquals(120.5, apollo?.illuminanceLux ?: 0.0, 0.1)
        assertEquals(640, apollo?.co2Ppm)
    }

    @Test
    fun testGetSmartHome_xmosVoiceSatellite() = runBlocking {
        val result = smartHomeRepository.getSmartHome()
        val xmos = result.getOrNull()?.voiceSatellites?.get("xvf3800")
        assertNotNull(xmos)

        assertEquals("listening", xmos?.state)
        assertEquals(185, xmos?.beamAngleDeg)
        assertEquals("Okay Nabu", xmos?.wakeWord)
        assertEquals("Living Room", xmos?.location)
    }

    @Test
    fun testGetSmartHome_sonoffDoorContact() = runBlocking {
        val result = smartHomeRepository.getSmartHome()
        val door = result.getOrNull()?.zigbeePerimeter?.get("sonoff_door")
        assertNotNull(door)

        assertEquals("closed", door?.state)
        assertEquals(95, door?.batteryPct)
        assertEquals(140, door?.lqi)
    }

    @Test
    fun testGetSmartHome_levoitAirPurifier() = runBlocking {
        val result = smartHomeRepository.getSmartHome()
        val purifier = result.getOrNull()?.airPurifier?.get("levoit_purifier")
        assertNotNull(purifier)

        assertEquals("on", purifier?.power)
        assertEquals("auto", purifier?.mode)
        assertEquals(2, purifier?.fanSpeed)
        assertEquals(4, purifier?.pm25Aqi)
        assertEquals("Excellent", purifier?.airQuality)
        assertEquals(88, purifier?.filterLifePct)
    }

    @Test
    fun testControlDevice_setPurifierFanSpeed() = runBlocking {
        val result = smartHomeRepository.setPurifierFanSpeed(3)
        assertTrue(result.isSuccess)
        val resp = result.getOrNull()
        assertNotNull(resp)
        assertTrue(resp?.success == true)
        assertEquals("levoit_purifier", resp?.device)
        assertEquals("set_fan_speed", resp?.action)
    }

    @Test
    fun testControlDevice_togglePurifierPower() = runBlocking {
        val result = smartHomeRepository.togglePurifierPower(turnOn = false)
        assertTrue(result.isSuccess)
        val resp = result.getOrNull()
        assertNotNull(resp)
        assertTrue(resp?.success == true)
    }
}
