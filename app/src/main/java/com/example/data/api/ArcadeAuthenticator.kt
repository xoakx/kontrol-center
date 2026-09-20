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
        // Prevent infinite loops if re-authentication fails
        if (responseCount(response) >= maxRetries) {
            return null
        }

        // Synchronously acquire a fresh JWT token
        val newToken = tokenRefresher() ?: return null

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
