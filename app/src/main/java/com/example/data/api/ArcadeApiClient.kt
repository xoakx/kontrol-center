package com.example.data.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory and builder for creating ArcadeApiService Retrofit instances
 * configured with dynamic mesh routing, Bearer JWT authentication, and JSON serialization.
 */
object ArcadeApiClient {

    fun create(
        endpointProvider: () -> String = { "http://100.111.123.93:8899" },
        tokenProvider: () -> String? = { "arcade_operator_master_key" },
        connectTimeoutSec: Long = 5L,
        readTimeoutSec: Long = 10L,
        okHttpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
    ): ArcadeApiService {
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()

        val client = okHttpClientBuilder
            .addInterceptor(DynamicEndpointInterceptor(endpointProvider))
            .addInterceptor(BearerAuthInterceptor(tokenProvider))
            .authenticator(ArcadeAuthenticator(tokenProvider))
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://100.111.123.93:8899/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ArcadeApiService::class.java)
    }
}
