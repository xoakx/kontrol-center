package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FleetStatusResponse(
    @Json(name = "agents") val agents: List<AgentStatusItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AgentStatusItem(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "service") val service: String,
    @Json(name = "category") val category: String,
    @Json(name = "description") val description: String,
    @Json(name = "icon") val icon: String? = null,
    @Json(name = "status") val status: AgentServiceStatus,
    @Json(name = "is_dynamic") val isDynamic: Boolean = false
)

@JsonClass(generateAdapter = true)
data class AgentServiceStatus(
    @Json(name = "active") val active: Boolean,
    @Json(name = "state") val state: String? = null,
    @Json(name = "pid") val pid: Long? = null,
    @Json(name = "memory_mb") val memoryMb: Double? = null
)

@JsonClass(generateAdapter = true)
data class AgentControlRequest(
    @Json(name = "service") val service: String,
    @Json(name = "action") val action: String // "start", "stop", "restart", "clear_state"
)

@JsonClass(generateAdapter = true)
data class AgentControlResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "service") val service: String? = null,
    @Json(name = "action") val action: String? = null,
    @Json(name = "status") val status: AgentControlStatus? = null,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class AgentControlStatus(
    @Json(name = "active") val active: Boolean,
    @Json(name = "state") val state: String
)
