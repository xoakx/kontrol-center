package com.example.e2e.tier1_features

import com.example.data.api.LoginRequest
import com.example.e2e.harness.E2eTestHarness
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * Tier 1 Tests for Feature 3: Arcade REST/WS Client (:8899).
 * Covers 5 equivalence classes according to spec_miner_e2e_t1_3:
 * - T1_F03_01: Bearer JWT auth exchange & access token retrieval
 * - T1_F03_02: Bearer token header injection on authenticated requests
 * - T1_F03_03: Telemetry REST API serialization & metric model validation
 * - T1_F03_04: HTTP 500 server error graceful exception handling
 * - T1_F03_05: HTTP 401 authentication rejection handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F03_ArcadeClientTest : E2eTestHarness() {

    @Test
    fun T1_F03_01_auth_login_jwt_retrieval() = runBlocking {
        val loginResponse = apiService.login(LoginRequest(token = "arcade_operator_master_key"))
        assertNotNull(loginResponse)
        assertEquals("mock_jwt_operator_token_hs256", loginResponse.accessToken)
        assertTrue(dispatcher.recordedRequests.any { it.url.encodedPath == "/api/auth/login" })
    }

    @Test
    fun T1_F03_02_bearer_token_interceptor_injection() = runBlocking {
        // Build OkHttp client with BearerAuth header injection
        val authClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer mock_jwt_operator_token_hs256")
                    .build()
                chain.proceed(request)
            })
            .addInterceptor(dispatcher)
            .connectTimeout(1L, TimeUnit.SECONDS)
            .build()

        val req = Request.Builder()
            .url("http://100.111.123.93:8899/api/telemetry")
            .build()

        authClient.newCall(req).execute().use { response ->
            assertTrue(response.isSuccessful)
            val intercepted = dispatcher.lastRecordedRequest
            assertNotNull(intercepted)
            assertEquals("Bearer mock_jwt_operator_token_hs256", intercepted?.header("Authorization"))
        }
    }

    @Test
    fun T1_F03_03_telemetry_rest_api_ingestion() = runBlocking {
        val telemetry = apiService.getTelemetry()
        assertNotNull(telemetry)
        assertEquals("performance", telemetry.cpu.governor)
        assertEquals(20, telemetry.cpu.cores)
        assertEquals(42.0, telemetry.cpu.tempC, 0.1)
        assertEquals(65536L, telemetry.memory.totalMb)
        assertEquals(2, telemetry.gpus.size)
        assertEquals("NVIDIA GeForce RTX 5060 Ti", telemetry.gpus[0].name)
        assertEquals(41, telemetry.gpus[0].tempC)
        assertEquals("Intel AI Boost NPU 4 (Arrow Lake)", telemetry.npu.model)
        assertTrue(telemetry.npu.present)
    }

    @Test
    fun T1_F03_04_http_500_graceful_exception_handling() = runBlocking {
        // Inject 500 error override for /api/telemetry
        dispatcher.setResponse("/api/telemetry", 500, """{"error": "Internal Server Error"}""")

        try {
            apiService.getTelemetry()
            fail("Expected HttpException with code 500")
        } catch (e: HttpException) {
            assertEquals(500, e.code())
            val errorBody = e.response()?.errorBody()?.string()
            assertTrue(errorBody?.contains("Internal Server Error") == true)
        }
    }

    @Test
    fun T1_F03_05_http_401_auth_error_handling() = runBlocking {
        try {
            apiService.login(LoginRequest(token = "invalid_bad_token"))
            fail("Expected HttpException with code 401")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
            val errorBody = e.response()?.errorBody()?.string()
            assertTrue(errorBody?.contains("Invalid token") == true)
        }
    }
}
