package com.example.data

import com.example.data.api.DynamicEndpointInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class DynamicEndpointInterceptorTest {

    private fun createChain(request: Request, capture: (Request) -> Unit): Interceptor.Chain {
        return object : Interceptor.Chain {
            override fun request(): Request = request
            override fun proceed(request: Request): Response {
                capture(request)
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("{}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            override fun connection() = null
            override fun call() = throw UnsupportedOperationException()
            override fun connectTimeoutMillis() = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun readTimeoutMillis() = 1000
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun writeTimeoutMillis() = 1000
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }
    }

    @Test
    fun intercept_whenOffline_throwsIOException() {
        val interceptor = DynamicEndpointInterceptor { "" }
        val request = Request.Builder().url("http://127.0.0.1:8899/api/telemetry").build()
        val chain = createChain(request) {}

        try {
            interceptor.intercept(chain)
            fail("Expected IOException when NetworkMesh is OFFLINE")
        } catch (e: IOException) {
            assertEquals("NetworkMesh is OFFLINE: no active endpoint available", e.message)
        }
    }

    @Test
    fun intercept_whenOnline_rewritesTargetHostAndPort() {
        val interceptor = DynamicEndpointInterceptor { "http://100.111.123.93:8899" }
        val request = Request.Builder().url("http://127.0.0.1:8899/api/telemetry").build()
        var rewrittenRequest: Request? = null
        val chain = createChain(request) { rewritten ->
            rewrittenRequest = rewritten
        }

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)
        assertEquals("http://100.111.123.93:8899/api/telemetry", rewrittenRequest?.url.toString())
    }

    @Test
    fun intercept_whenProviderThrows_wrapsInIOException() {
        val interceptor = DynamicEndpointInterceptor { throw RuntimeException("Mesh error") }
        val request = Request.Builder().url("http://127.0.0.1:8899/api/telemetry").build()
        val chain = createChain(request) {}

        try {
            interceptor.intercept(chain)
            fail("Expected IOException when provider throws")
        } catch (e: IOException) {
            org.junit.Assert.assertTrue(e.message?.contains("Failed to resolve dynamic endpoint") == true)
        }
    }
}
