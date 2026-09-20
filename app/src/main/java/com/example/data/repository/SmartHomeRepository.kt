package com.example.data.repository

import android.util.Log
import com.example.data.api.AirPurifierDto
import com.example.data.api.ArcadeApiService
import com.example.data.api.SensorDeviceDto
import com.example.data.api.SmartHomeControlRequest
import com.example.data.api.SmartHomeControlResponse
import com.example.data.api.SmartHomeResponse
import com.example.data.api.VoiceSatelliteDto
import com.example.data.api.ZigbeeContactDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * SmartHomeRepository manages telemetry and remote actions for host IoT and environment sensors:
 * - Apollo MSR-2 mmWave Radar (living room target tracking, distance, energy, lux, CO2 ppm)
 * - XMOS XVF3800 Voice Satellite DSP (beam tracking angle, wake word intents)
 * - Sonoff Zigbee perimeter door contact (state, battery %, LQI)
 * - Levoit Smart Air Purifier Core 400S (power, fan speed, PM2.5 AQI, air quality, filter life %)
 */
class SmartHomeRepository(
    private val apiService: ArcadeApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "SmartHomeRepository"
    }

    private val _smartHome = MutableStateFlow<SmartHomeResponse?>(null)
    val smartHome: StateFlow<SmartHomeResponse?> = _smartHome.asStateFlow()

    val apolloRadar: Flow<SensorDeviceDto?> = _smartHome.map { it?.sensors?.get("apollo_msr2") }
    val xmosSatellite: Flow<VoiceSatelliteDto?> = _smartHome.map { it?.voiceSatellites?.get("xvf3800") }
    val sonoffDoor: Flow<ZigbeeContactDto?> = _smartHome.map { it?.zigbeePerimeter?.get("sonoff_door") }
    val levoitPurifier: Flow<AirPurifierDto?> = _smartHome.map { it?.airPurifier?.get("levoit_purifier") }

    suspend fun getSmartHome(): Result<SmartHomeResponse> = withContext(ioDispatcher) {
        try {
            val response = apiService.getSmartHome()
            _smartHome.value = response
            Result.success(response)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "getSmartHome failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun controlDevice(
        category: String,
        id: String,
        action: String,
        value: Int? = null
    ): Result<SmartHomeControlResponse> = withContext(ioDispatcher) {
        try {
            val request = SmartHomeControlRequest(
                category = category,
                id = id,
                device = id,
                action = action,
                value = value
            )
            val response = apiService.controlSmartHome(request)
            if (id == "levoit_purifier" && action == "set_fan_speed" && value != null) {
                updatePurifierState { it.copy(fanSpeed = value) }
            } else if (id == "levoit_purifier" && (action == "toggle_power" || action == "toggle_purifier_power" || action == "turn_on" || action == "turn_off")) {
                updatePurifierState { purifier ->
                    val newPower = when (action) {
                        "turn_on" -> "on"
                        "turn_off" -> "off"
                        else -> if (purifier.power == "on") "off" else "on"
                    }
                    purifier.copy(power = newPower)
                }
            }
            Result.success(response)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "controlDevice failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun setPurifierFanSpeed(speed: Int): Result<SmartHomeControlResponse> =
        controlDevice(category = "air_purifier", id = "levoit_purifier", action = "set_fan_speed", value = speed)

    suspend fun togglePurifierPower(turnOn: Boolean? = null): Result<SmartHomeControlResponse> {
        val action = if (turnOn != null) {
            if (turnOn) "turn_on" else "turn_off"
        } else "toggle_purifier_power"
        return controlDevice(category = "air_purifier", id = "levoit_purifier", action = action)
    }

    private fun updatePurifierState(updater: (AirPurifierDto) -> AirPurifierDto) {
        _smartHome.update { cur ->
            if (cur == null) return@update null
            val currentPurifier = cur.airPurifier["levoit_purifier"] ?: return@update cur
            val updated = updater(currentPurifier)
            val newMap = cur.airPurifier.toMutableMap()
            newMap["levoit_purifier"] = updated
            cur.copy(airPurifier = newMap)
        }
    }
}
