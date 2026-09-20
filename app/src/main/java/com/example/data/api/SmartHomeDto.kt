package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SmartHomeResponse(
    @Json(name = "sensors") val sensors: Map<String, SensorDeviceDto> = emptyMap(),
    @Json(name = "voice_satellites") val voiceSatellites: Map<String, VoiceSatelliteDto> = emptyMap(),
    @Json(name = "zigbee_perimeter") val zigbeePerimeter: Map<String, ZigbeeContactDto> = emptyMap(),
    @Json(name = "air_purifier") val airPurifier: Map<String, AirPurifierDto> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class SensorDeviceDto(
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String = "mmwave_multisensor",
    @Json(name = "presence") val presence: Boolean = false,
    @Json(name = "target_distance_m") val targetDistanceM: Double = 0.0,
    @Json(name = "movement_energy") val movementEnergy: Int = 0,
    @Json(name = "still_energy") val stillEnergy: Int = 0,
    @Json(name = "illuminance_lux") val illuminanceLux: Double = 0.0,
    @Json(name = "temperature_c") val temperatureC: Double = 0.0,
    @Json(name = "co2_ppm") val co2Ppm: Int = 0,
    @Json(name = "firmware") val firmware: String = ""
)

@JsonClass(generateAdapter = true)
data class VoiceSatelliteDto(
    @Json(name = "name") val name: String,
    @Json(name = "location") val location: String = "",
    @Json(name = "type") val type: String = "dsp_voice_processor",
    @Json(name = "state") val state: String = "idle",
    @Json(name = "beam_angle_deg") val beamAngleDeg: Int = 0,
    @Json(name = "wake_word") val wakeWord: String = "",
    @Json(name = "last_intent") val lastIntent: String = "",
    @Json(name = "firmware") val firmware: String = ""
)

@JsonClass(generateAdapter = true)
data class ZigbeeContactDto(
    @Json(name = "name") val name: String,
    @Json(name = "model") val model: String = "",
    @Json(name = "state") val state: String = "closed",
    @Json(name = "battery_pct") val batteryPct: Int = 100,
    @Json(name = "lqi") val lqi: Int = 0,
    @Json(name = "last_changed") val lastChanged: String = ""
)

@JsonClass(generateAdapter = true)
data class AirPurifierDto(
    @Json(name = "name") val name: String,
    @Json(name = "model") val model: String = "",
    @Json(name = "power") val power: String = "off",
    @Json(name = "mode") val mode: String = "auto",
    @Json(name = "fan_speed") val fanSpeed: Int = 0,
    @Json(name = "pm25_aqi") val pm25Aqi: Int = 0,
    @Json(name = "air_quality") val airQuality: String = "Good",
    @Json(name = "filter_life_pct") val filterLifePct: Int = 100,
    @Json(name = "display_light") val displayLight: String = "dim"
)

@JsonClass(generateAdapter = true)
data class SmartHomeControlRequest(
    @Json(name = "category") val category: String? = null,
    @Json(name = "id") val id: String? = null,
    @Json(name = "device") val device: String? = null,
    @Json(name = "action") val action: String,
    @Json(name = "value") val value: Int? = null
)

@JsonClass(generateAdapter = true)
data class SmartHomeControlResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "device") val device: String? = null,
    @Json(name = "action") val action: String? = null,
    @Json(name = "message") val message: String? = null
)

