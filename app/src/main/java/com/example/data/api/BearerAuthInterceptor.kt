package com.example.data.api

import okhttp3.Interceptor
import okhttp3.Response

class BearerAuthInterceptor(
    private val tokenProvider: () -> String?
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath

        // Do not add Authorization header to login request
        if (path.endsWith("/api/auth/login")) {
            return chain.proceed(original)
        }

        val token = tokenProvider()
        return if (!token.isNullOrBlank()) {
            val authenticated = original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
            chain.proceed(authenticated)
        } else {
            chain.proceed(original)
        }
    }
}
