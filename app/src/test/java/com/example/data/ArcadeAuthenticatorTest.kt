package com.example.data

import com.example.data.api.ArcadeAuthenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ArcadeAuthenticatorTest {

    private fun createResponse(
        url: String,
        authHeader: String? = null,
        priorResponse: Response? = null,
        hasBody: Boolean = true
    ): Response {
        val requestBuilder = Request.Builder().url(url)
        if (authHeader != null) {
            requestBuilder.header("Authorization", authHeader)
        }
        val request = requestBuilder.build()

        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")

        if (hasBody) {
            builder.body("{}".toResponseBody("application/json".toMediaType()))
        }

        if (priorResponse != null) {
            builder.priorResponse(priorResponse)
        }

        return builder.build()
    }

    @Test
    fun authenticate_onLoginRoute_returnsNull() {
        val authenticator = ArcadeAuthenticator(tokenRefresher = { "valid-jwt-token" })
        val response = createResponse("http://127.0.0.1:8899/api/auth/login")

        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }

    @Test
    fun authenticate_whenTokenBlank_returnsNull() {
        val authenticator = ArcadeAuthenticator(tokenRefresher = { "   " })
        val response = createResponse("http://127.0.0.1:8899/api/telemetry")

        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }

    @Test
    fun authenticate_whenTokenRefresherThrows_returnsNull() {
        val authenticator = ArcadeAuthenticator(tokenRefresher = { throw IllegalStateException("KeyStore locked") })
        val response = createResponse("http://127.0.0.1:8899/api/telemetry")

        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }

    @Test
    fun authenticate_whenTokenIdenticalToRejected_returnsNull() {
        val authenticator = ArcadeAuthenticator(tokenRefresher = { "stale-token-123" })
        val response = createResponse("http://127.0.0.1:8899/api/telemetry", authHeader = "Bearer stale-token-123")

        val result = authenticator.authenticate(null, response)
        assertNull(result)
    }

    @Test
    fun authenticate_whenNewTokenAvailable_returnsAuthenticatedRequest() {
        val authenticator = ArcadeAuthenticator(tokenRefresher = { "fresh-jwt-token-456" })
        val response = createResponse("http://127.0.0.1:8899/api/telemetry", authHeader = "Bearer stale-token-123")

        val result = authenticator.authenticate(null, response)
        assertNotNull(result)
        assertEquals("Bearer fresh-jwt-token-456", result?.header("Authorization"))
    }

    @Test
    fun authenticate_whenExceedingMaxRetries_returnsNull() {
        val authenticator = ArcadeAuthenticator(tokenRefresher = { "fresh-token" }, maxRetries = 2)

        val firstResponse = createResponse("http://127.0.0.1:8899/api/telemetry", hasBody = false)
        val secondResponse = createResponse("http://127.0.0.1:8899/api/telemetry", priorResponse = firstResponse)

        // responseCount is 2, matching maxRetries 2 -> abort
        val result = authenticator.authenticate(null, secondResponse)
        assertNull(result)
    }
}
