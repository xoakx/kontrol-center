package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NetSecOverviewResponse(
    @Json(name = "status") val status: String = "healthy",
    @Json(name = "suricata_active") val suricataActive: Boolean = true,
    @Json(name = "suricata_alert_count") val suricataAlertCount: Int = 0,
    @Json(name = "crowdsec_active") val crowdsecActive: Boolean = true,
    @Json(name = "crowdsec_ban_count") val crowdsecBanCount: Int = 0,
    @Json(name = "tetragon_health") val tetragonHealth: String = "healthy",
    @Json(name = "total_dropped_packets") val totalDroppedPackets: Long = 0
)

@JsonClass(generateAdapter = true)
data class SuricataAlertsResponse(
    @Json(name = "status") val status: String = "success",
    @Json(name = "count") val count: Int = 0,
    @Json(name = "alerts") val alerts: List<SuricataAlertItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SuricataAlertItem(
    @Json(name = "timestamp") val timestamp: String,
    @Json(name = "event_type") val eventType: String = "alert",
    @Json(name = "src_ip") val srcIp: String = "",
    @Json(name = "src_port") val srcPort: Int? = null,
    @Json(name = "dest_ip") val destIp: String = "",
    @Json(name = "dest_port") val destPort: Int? = null,
    @Json(name = "proto") val proto: String = "TCP",
    @Json(name = "alert") val alert: SuricataAlertDetails
)

@JsonClass(generateAdapter = true)
data class SuricataAlertDetails(
    @Json(name = "signature") val signature: String,
    @Json(name = "category") val category: String = "",
    @Json(name = "severity") val severity: Int = 3,
    @Json(name = "signature_id") val signatureId: Long? = null
)

@JsonClass(generateAdapter = true)
data class CrowdSecDecisionsResponse(
    @Json(name = "status") val status: String = "success",
    @Json(name = "action") val action: String = "list",
    @Json(name = "active_decisions") val activeDecisions: List<CrowdSecDecisionItem> = emptyList(),
    @Json(name = "decision_count") val decisionCount: Int = 0,
    @Json(name = "bouncers") val bouncers: List<CrowdSecBouncerItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class CrowdSecDecisionItem(
    @Json(name = "id") val id: Long,
    @Json(name = "origin") val origin: String = "cscli",
    @Json(name = "type") val type: String = "ban",
    @Json(name = "scope") val scope: String = "Ip",
    @Json(name = "value") val value: String,
    @Json(name = "duration") val duration: String = "",
    @Json(name = "until") val until: String = "",
    @Json(name = "scenario") val scenario: String = ""
)

@JsonClass(generateAdapter = true)
data class CrowdSecBouncerItem(
    @Json(name = "name") val name: String,
    @Json(name = "type") val type: String = "",
    @Json(name = "valid") val valid: Boolean = true
)

@JsonClass(generateAdapter = true)
data class UnbanRequest(
    @Json(name = "ip") val ip: String
)

@JsonClass(generateAdapter = true)
data class UnbanResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "action") val action: String = "delete",
    @Json(name = "ip") val ip: String = "",
    @Json(name = "output") val output: String = "",
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class TetragonStatusResponse(
    @Json(name = "status") val status: String = "healthy",
    @Json(name = "tracing_policies") val tracingPolicies: List<TetragonPolicyItem> = emptyList(),
    @Json(name = "recent_events") val recentEvents: List<TetragonEventItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TetragonPolicyItem(
    @Json(name = "name") val name: String,
    @Json(name = "mode") val mode: String = "monitor_only"
)

@JsonClass(generateAdapter = true)
data class TetragonEventItem(
    @Json(name = "process_kprobe") val processKprobe: TetragonProcessKprobe? = null
)

@JsonClass(generateAdapter = true)
data class TetragonProcessKprobe(
    @Json(name = "binary") val binary: String? = null,
    @Json(name = "pid") val pid: Long? = null,
    @Json(name = "function_name") val functionName: String? = null,
    @Json(name = "action") val action: String? = null
)

@JsonClass(generateAdapter = true)
data class FirewallStatusResponse(
    @Json(name = "status") val status: String = "active",
    @Json(name = "tableCount") val tableCount: Int = 0,
    @Json(name = "totalDroppedPackets") val totalDroppedPackets: Long = 0,
    @Json(name = "dropRules") val dropRules: List<FirewallDropRule> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FirewallDropRule(
    @Json(name = "chain") val chain: String,
    @Json(name = "packets") val packets: Long = 0
)

