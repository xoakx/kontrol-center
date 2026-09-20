package com.example.data.api

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

class DynamicEndpointInterceptor(
    private val endpointProvider: () -> String
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val targetBaseUrl = endpointProvider().trim()
        val newHttpUrl = targetBaseUrl.toHttpUrlOrNull()

        if (newHttpUrl != null) {
            val rewrittenUrl = request.url.newBuilder()
                .scheme(newHttpUrl.scheme)
                .host(newHttpUrl.host)
                .port(newHttpUrl.port)
                .build()
            request = request.newBuilder().url(rewrittenUrl).build()
        }
        return chain.proceed(request)
    }
}
