package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WsEventFrame(
    @Json(name = "type") val type: String, // "telemetry" or "event"
    @Json(name = "data") val data: Map<String, Any?>
)

@JsonClass(generateAdapter = true)
data class ArcadeEventBusMessage(
    @Json(name = "id") val id: Long = 0,
    @Json(name = "topic") val topic: String,
    @Json(name = "source") val source: String,
    @Json(name = "payload") val payload: Map<String, Any?> = emptyMap(),
    @Json(name = "timestamp") val timestamp: String
)
