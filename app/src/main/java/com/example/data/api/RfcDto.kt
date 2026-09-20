package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RfcsResponse(
    @Json(name = "rfcs") val rfcs: List<RfcItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RfcItem(
    @Json(name = "id") val id: String,
    @Json(name = "source") val source: String,
    @Json(name = "title") val title: String,
    @Json(name = "category") val category: String,
    @Json(name = "description") val description: String,
    @Json(name = "proposed_steps") val proposedSteps: List<String> = emptyList(),
    @Json(name = "risk_level") val riskLevel: String = "MEDIUM",
    @Json(name = "status") val status: String = "PROPOSED",
    @Json(name = "task_id") val taskId: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
    @Json(name = "resolved_by") val resolvedBy: String? = null
)

@JsonClass(generateAdapter = true)
data class RfcVoteRequest(
    @Json(name = "decision") val decision: String // "approve" or "deny"
)

@JsonClass(generateAdapter = true)
data class RfcVoteResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "status") val status: String,
    @Json(name = "task_id") val taskId: String? = null
)
