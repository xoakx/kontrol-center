package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TelemetryResponse(
    @Json(name = "cpu") val cpu: CpuTelemetryDto,
    @Json(name = "memory") val memory: MemoryTelemetryDto,
    @Json(name = "storage") val storage: StorageTelemetryDto,
    @Json(name = "npu") val npu: NpuTelemetryDto,
    @Json(name = "gpus") val gpus: List<GpuTelemetryDto> = emptyList(),
    @Json(name = "network") val network: NetworkTelemetryDto,
    @Json(name = "uptime_hours") val uptimeHours: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class CpuTelemetryDto(
    @Json(name = "load_1m") val load1m: String = "0.0",
    @Json(name = "load_5m") val load5m: String = "0.0",
    @Json(name = "load_15m") val load15m: String = "0.0",
    @Json(name = "governor") val governor: String = "performance",
    @Json(name = "temp_c") val tempC: Double = 0.0,
    @Json(name = "cores") val cores: Int = 20
)

@JsonClass(generateAdapter = true)
data class MemoryTelemetryDto(
    @Json(name = "total_mb") val totalMb: Long = 0,
    @Json(name = "used_mb") val usedMb: Long = 0,
    @Json(name = "used_pct") val usedPct: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class StorageTelemetryDto(
    @Json(name = "total_gb") val totalGb: Double = 0.0,
    @Json(name = "used_gb") val usedGb: Double = 0.0,
    @Json(name = "free_gb") val freeGb: Double = 0.0,
    @Json(name = "used_pct") val usedPct: Double = 0.0,
    @Json(name = "mount") val mount: String = "/"
)

@JsonClass(generateAdapter = true)
data class NpuTelemetryDto(
    @Json(name = "present") val present: Boolean = false,
    @Json(name = "model") val model: String = "",
    @Json(name = "device") val device: String = "",
    @Json(name = "service") val service: String = ""
)

@JsonClass(generateAdapter = true)
data class GpuTelemetryDto(
    @Json(name = "index") val index: String = "0",
    @Json(name = "name") val name: String = "",
    @Json(name = "temp_c") val tempC: Int = 0,
    @Json(name = "util_pct") val utilPct: Int = 0,
    @Json(name = "mem_used_mb") val memUsedMb: Int = 0,
    @Json(name = "mem_total_mb") val memTotalMb: Int = 16384,
    @Json(name = "power_w") val powerW: Double = 0.0,
    @Json(name = "fan_speed_pct") val fanSpeedPct: Int = 0,
    @Json(name = "clock_mhz") val clockMhz: Int = 0
)

@JsonClass(generateAdapter = true)
data class NetworkTelemetryDto(
    @Json(name = "congestion_control") val congestionControl: String = "bbr",
    @Json(name = "qdisc") val qdisc: String = "fq"
)

@JsonClass(generateAdapter = true)
data class DispatchRequest(
    @Json(name = "action") val action: String
)

@JsonClass(generateAdapter = true)
data class DispatchResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "action") val action: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "output") val output: String? = null
)

