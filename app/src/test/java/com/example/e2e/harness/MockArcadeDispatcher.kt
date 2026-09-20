package com.example.e2e.harness

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Full OkHttp client interceptor & mock dispatcher implementing all Arcade API routes:
 * - /api/auth/login
 * - /api/telemetry
 * - /api/fleet/status & /api/agents
 * - /api/agents/control
 * - /api/smarthome
 * - /api/smarthome/control
 * - /api/rfcs
 * - /api/rfcs/{id}/vote & /api/rfcs/respond
 * - /api/dispatch
 * - /api/netsec/ (overview, suricata alerts, crowdsec decisions, unban, tetragon, firewall, bouncers)
 *
 * Intercepts OkHttp requests hermetically in-memory with deterministic fidelity and zero network egress.
 */
class MockArcadeDispatcher : Interceptor {

    val recordedRequests = CopyOnWriteArrayList<Request>()
    private val pathOverrides = ConcurrentHashMap<String, (Request) -> Response>()
    private val rawPayloadOverrides = ConcurrentHashMap<String, Pair<Int, String>>()

    @Volatile
    var simulateNetworkFailure: Boolean = false
    @Volatile
    var customFailureException: IOException? = null

    val lastRecordedRequest: Request?
        get() = recordedRequests.lastOrNull()

    fun overrideResponse(pathPrefix: String, responseProvider: (Request) -> Response) {
        pathOverrides[pathPrefix] = responseProvider
    }

    fun setResponse(pathPrefix: String, statusCode: Int, jsonBody: String) {
        rawPayloadOverrides[pathPrefix] = Pair(statusCode, jsonBody)
    }

    fun clearOverrides() {
        pathOverrides.clear()
        rawPayloadOverrides.clear()
        simulateNetworkFailure = false
        customFailureException = null
    }

    fun reset() {
        clearOverrides()
        recordedRequests.clear()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        recordedRequests.add(request)

        if (simulateNetworkFailure) {
            throw customFailureException ?: IOException("Simulated network socket timeout/refusal")
        }

        val path = request.url.encodedPath

        // 1. Check functional overrides
        for ((prefix, provider) in pathOverrides) {
            if (path.startsWith(prefix)) {
                return provider(request)
            }
        }

        // 2. Check raw payload overrides
        for ((prefix, pair) in rawPayloadOverrides) {
            if (path.startsWith(prefix)) {
                return createResponse(request, pair.first, pair.second)
            }
        }

        // 3. Dispatch to authoritative routes
        return dispatchDefaultRoute(request, path)
    }

    private fun dispatchDefaultRoute(request: Request, path: String): Response {
        val method = request.method.uppercase()

        return when {
            // AUTH: /api/auth/login
            path.startsWith("/api/auth/login") -> {
                val body = extractRequestBody(request)
                if (body.contains("invalid") || body.contains("bad_token")) {
                    createResponse(request, 401, """{"detail": "Invalid token"}""")
                } else {
                    createResponse(
                        request,
                        200,
                        """{"access_token": "mock_jwt_operator_token_hs256", "token_type": "bearer"}"""
                    )
                }
            }

            // TELEMETRY: /api/telemetry
            path.startsWith("/api/telemetry") -> {
                createResponse(request, 200, MockTelemetryPayloads.standardDualGpuTelemetryJson())
            }

            // FLEET DAEMONS: /api/fleet/status or /api/agents
            path.startsWith("/api/fleet/status") || path == "/api/agents" -> {
                createResponse(request, 200, MockTelemetryPayloads.all12FleetDaemonsJson())
            }

            // FLEET CONTROL: /api/agents/control
            path.startsWith("/api/agents/control") -> {
                val body = extractRequestBody(request)
                val service = extractJsonStringField(body, "service") ?: "gemini-scribe.service"
                val action = extractJsonStringField(body, "action") ?: "restart"
                val isRunning = action != "stop"
                createResponse(
                    request,
                    200,
                    """
                    {
                      "success": true,
                      "service": "$service",
                      "action": "$action",
                      "status": {
                        "active": $isRunning,
                        "state": "${if (isRunning) "running" else "stopped"}"
                      }
                    }
                    """.trimIndent()
                )
            }

            // SMART HOME: /api/smarthome
            path == "/api/smarthome" -> {
                createResponse(request, 200, MockTelemetryPayloads.standardSmartHomeJson())
            }

            // SMART HOME CONTROL: /api/smarthome/control
            path.startsWith("/api/smarthome/control") -> {
                val body = extractRequestBody(request)
                val deviceId = extractJsonStringField(body, "id") ?: extractJsonStringField(body, "device") ?: "levoit_purifier"
                val action = extractJsonStringField(body, "action") ?: "set_fan_speed"
                createResponse(
                    request,
                    200,
                    """{"success": true, "device": "$deviceId", "action": "$action", "message": "Device state updated."}"""
                )
            }

            // RFCS: GET /api/rfcs
            path == "/api/rfcs" && method == "GET" -> {
                createResponse(request, 200, MockTelemetryPayloads.pendingRfcsJson())
            }

            // RFC VOTING: POST /api/rfcs/{id}/vote or /api/rfcs/respond
            (path.contains("/vote") || path.startsWith("/api/rfcs/respond")) && method == "POST" -> {
                val body = extractRequestBody(request)
                val decision = extractJsonStringField(body, "decision") ?: "approve"
                val rfcIdFromPath = path.substringAfter("/api/rfcs/").substringBefore("/vote")
                val rfcId = if (rfcIdFromPath.isNotBlank() && rfcIdFromPath != path) rfcIdFromPath else "RFC-00142"

                if (decision.lowercase() == "approve") {
                    createResponse(
                        request,
                        200,
                        """{"success": true, "rfc_id": "$rfcId", "status": "APPROVED", "task_id": "task_${rfcId.lowercase()}_a1b2"}"""
                    )
                } else {
                    createResponse(
                        request,
                        200,
                        """{"success": true, "rfc_id": "$rfcId", "status": "REJECTED"}"""
                    )
                }
            }

            // DISPATCH ACTIONS: /api/dispatch
            path.startsWith("/api/dispatch") -> {
                val body = extractRequestBody(request)
                val action = extractJsonStringField(body, "action") ?: "sre_sweep"
                val message = when (action) {
                    "perf_lock" -> "Performance profile activated via TuneD (throughput-performance) and locked to all 20 cores."
                    "sre_sweep" -> "SRE Scan Complete: 0 failed units found."
                    "git_sync" -> "Git Audit: Working tree clean and committed."
                    "audio_reanchor" -> "Audio pipeline re-anchored successfully."
                    else -> "Action '$action' executed successfully."
                }
                createResponse(
                    request,
                    200,
                    """{"success": true, "action": "$action", "message": "$message"}"""
                )
            }

            // NETSEC OVERVIEW: /api/netsec/overview
            path.startsWith("/api/netsec/overview") -> {
                createResponse(request, 200, MockNetSecPayloads.netsecOverviewJson())
            }

            // SURICATA ALERTS: /api/netsec/suricata/alerts
            path.startsWith("/api/netsec/suricata/alerts") -> {
                createResponse(request, 200, MockNetSecPayloads.suricataAlertsJson())
            }

            // CROWDSEC DECISIONS: /api/netsec/crowdsec/decisions
            path.startsWith("/api/netsec/crowdsec/decisions") -> {
                createResponse(request, 200, MockNetSecPayloads.crowdsecDecisionsJson())
            }

            // CROWDSEC UNBAN: /api/netsec/crowdsec/unban
            path.startsWith("/api/netsec/crowdsec/unban") -> {
                val body = extractRequestBody(request)
                val ip = extractJsonStringField(body, "ip") ?: "198.51.100.42"
                createResponse(
                    request,
                    200,
                    """{"success": true, "message": "Decision deleted", "ip": "$ip"}"""
                )
            }

            // TETRAGON STATUS: /api/netsec/tetragon/status
            path.startsWith("/api/netsec/tetragon/status") -> {
                createResponse(request, 200, MockNetSecPayloads.tetragonStatusJson())
            }

            // FIREWALL STATUS: /api/netsec/firewall/status
            path.startsWith("/api/netsec/firewall/status") -> {
                createResponse(request, 200, MockNetSecPayloads.firewallStatusJson())
            }

            // BOUNCERS: /api/netsec/crowdsec/bouncers
            path.startsWith("/api/netsec/crowdsec/bouncers") -> {
                createResponse(request, 200, MockNetSecPayloads.bouncersStatusJson())
            }

            // 404 FALLBACK
            else -> {
                createResponse(
                    request,
                    404,
                    """{"error": "Endpoint '$path' not found in MockArcadeDispatcher"}"""
                )
            }
        }
    }

    fun createResponse(
        request: Request,
        code: Int,
        bodyJson: String,
        contentType: String = "application/json; charset=utf-8"
    ): Response {
        val mediaType = contentType.toMediaType()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error $code")
            .header("Content-Type", contentType)
            .body(bodyJson.toResponseBody(mediaType))
            .build()
    }

    fun extractRequestBody(request: Request): String {
        return try {
            val copy = request.newBuilder().build()
            val buffer = Buffer()
            copy.body?.writeTo(buffer)
            buffer.readString(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    private fun extractJsonStringField(json: String, fieldName: String): String? {
        val regex = Regex("""\"$fieldName\"\s*:\s*\"([^\"]+)\"""")
        return regex.find(json)?.groupValues?.get(1)
    }
}
