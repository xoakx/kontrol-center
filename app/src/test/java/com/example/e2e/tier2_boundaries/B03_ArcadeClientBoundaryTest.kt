package com.example.e2e.tier2_boundaries

import com.example.data.api.LoginRequest
import com.example.e2e.harness.E2eTestHarness
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketException

/**
 * Tier 2 Boundary Tests: B03 Arcade Client Boundary.
 * Covers 5 boundary conditions:
 * - T2_B03_01: HTTP 500 Internal Server Error handling across REST endpoints
 * - T2_B03_02: HTTP 503 Service Unavailable / Maintenance mode error handling
 * - T2_B03_03: Malformed, truncated, or schema-violating JSON payload deserialization
 * - T2_B03_04: Abrupt socket reset / broken pipe (SocketException) injection
 * - T2_B03_05: Token expiration HTTP 401 unauthorized rejection and loop prevention
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class B03_ArcadeClientBoundaryTest : E2eTestHarness() {

    @Test
    fun T2_B03_01_http_500_internal_server_error() = runBlocking {
        dispatcher.setResponse("/api/telemetry", 500, """{"detail": "Crash in telemetry aggregator worker"}""")

        try {
            apiService.getTelemetry()
            fail("Expected HttpException(500)")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
            val body = e.response()?.errorBody()?.string()
            assertNotNull(body)
            assertTrue(body?.contains("Crash in telemetry aggregator") == true)
        }
    }

    @Test
    fun T2_B03_02_http_503_service_unavailable() = runBlocking {
        dispatcher.setResponse(
            "/api/fleet/status",
            503,
            """{"detail": "Arcade daemon reloading configuration. Service unavailable."}"""
        )

        try {
            apiService.getFleetStatus()
            fail("Expected HttpException(503)")
        } catch (e: HttpException) {
            assertEquals(503, e.code())
            val body = e.response()?.errorBody()?.string()
            assertTrue(body?.contains("Service unavailable") == true)
        }
    }

    @Test
    fun T2_B03_03_malformed_json_response() = runBlocking {
        // Inject truncated/corrupted JSON response body
        dispatcher.setResponse("/api/telemetry", 200, """{"cpu": {"cores": "STRING_NOT_INT", "temp_c": """)

        try {
            apiService.getTelemetry()
            fail("Expected Moshi JSON parsing exception")
        } catch (e: Exception) {
            assertTrue(
                "Expected JSON encoding or data exception, got ${e.javaClass.simpleName}",
                e is JsonDataException || e is JsonEncodingException || e is IOException
            )
        }
    }

    @Test
    fun T2_B03_04_abrupt_socket_reset_broken_pipe() = runBlocking {
        dispatcher.simulateNetworkFailure = true
        dispatcher.customFailureException = SocketException("Connection reset by peer (ECONNRESET)")

        try {
            apiService.getTelemetry()
            fail("Expected SocketException")
        } catch (e: IOException) {
            assertTrue(e is SocketException)
            assertTrue(e.message?.contains("ECONNRESET") == true)
        } finally {
            dispatcher.clearOverrides()
        }
    }

    @Test
    fun T2_B03_05_token_expiration_refresh_loop() = runBlocking {
        // Simulate expired token
        dispatcher.setResponse(
            "/api/auth/login",
            401,
            """{"detail": "Token expired at 2026-09-19T18:00:00Z"}"""
        )

        var retryCount = 0
        val maxRetries = 3
        var authenticated = false

        while (retryCount < maxRetries && !authenticated) {
            try {
                apiService.login(LoginRequest(token = "expired_token_123"))
                authenticated = true
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    retryCount++
                } else {
                    throw e
                }
            }
        }

        // Must terminate after maxRetries without runaway infinite loop
        assertEquals(3, retryCount)
        assertTrue(!authenticated)
    }
}
