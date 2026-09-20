package com.example.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

class DynamicEndpointInterceptor(
    private val endpointProvider: () -> String
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val targetBaseUrl = try {
            endpointProvider().trim()
        } catch (e: Exception) {
            throw IOException("Failed to resolve dynamic endpoint: ${e.message}", e)
        }

        // Fail-fast when offline rather than waiting for socket connection timeout
        if (targetBaseUrl.isBlank()) {
            throw IOException("NetworkMesh is OFFLINE: no active endpoint available")
        }

        val newHttpUrl = targetBaseUrl.toHttpUrlOrNull()
            ?: throw IOException("Invalid active endpoint URL: '$targetBaseUrl'")

        val rewrittenUrl = request.url.newBuilder()
            .scheme(newHttpUrl.scheme)
            .host(newHttpUrl.host)
            .port(newHttpUrl.port)
            .build()

        request = request.newBuilder().url(rewrittenUrl).build()
        return chain.proceed(request)
    }
}
