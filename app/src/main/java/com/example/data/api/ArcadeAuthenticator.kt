package com.example.data.api

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class ArcadeAuthenticator(
    private val tokenRefresher: () -> String?,
    private val maxRetries: Int = 2
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Do not attempt re-authentication on the login route itself
        if (response.request.url.encodedPath.endsWith("/api/auth/login")) {
            return null
        }

        // Prevent infinite loops if re-authentication fails repeatedly
        if (responseCount(response) >= maxRetries) {
            return null
        }

        // Synchronously acquire a fresh JWT token, catching any network or crypto exceptions
        val newToken = try {
            tokenRefresher()?.trim()
        } catch (e: Exception) {
            return null
        }

        if (newToken.isNullOrBlank()) {
            return null
        }

        // If the request was already attempted with this exact token, give up to prevent tight retry loops
        val existingHeader = response.request.header("Authorization")
        if (existingHeader == "Bearer $newToken") {
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }
}
