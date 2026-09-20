package com.example.data

import com.example.data.api.ArcadeWebSocketClient
import com.example.data.api.WsConnectionStatus
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class ArcadeWebSocketClientTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val moshi = Moshi.Builder().build()
    private val okHttpClient = OkHttpClient.Builder().build()

    @Test
    fun connect_whenOffline_doesNotCrash_andRemainsInDisconnectedState() = testScope.runTest {
        var providerCallCount = 0
        val client = ArcadeWebSocketClient(
            okHttpClient = okHttpClient,
            moshi = moshi,
            endpointUrlProvider = {
                providerCallCount++
                "" // Simulating NetworkMeshManager OFFLINE
            },
            scope = this,
            ioDispatcher = testDispatcher
        )

        // Starting client while offline must not throw IllegalArgumentException: unexpected host: :8899
        client.start()
        assertEquals(WsConnectionStatus.DISCONNECTED, client.connectionState.value)
        assertEquals(1, providerCallCount)

        // Advance 1000ms: first reconnect delay
        testScheduler.advanceTimeBy(1001L)
        assertEquals(WsConnectionStatus.DISCONNECTED, client.connectionState.value)
        assertEquals(2, providerCallCount)

        // Advance 2000ms: exponential backoff reconnect delay
        testScheduler.advanceTimeBy(2001L)
        assertEquals(WsConnectionStatus.DISCONNECTED, client.connectionState.value)
        assertEquals(3, providerCallCount)

        client.stop()
    }

    @Test
    fun connect_whenMalformedUrl_doesNotCrash_andTransitionsToFailed() = testScope.runTest {
        val client = ArcadeWebSocketClient(
            okHttpClient = okHttpClient,
            moshi = moshi,
            endpointUrlProvider = { "http://:::bad-host-url" },
            scope = this,
            ioDispatcher = testDispatcher
        )

        client.start()
        // Synchronous exception caught in try-catch transitions state to FAILED without crashing coroutine
        assertEquals(WsConnectionStatus.FAILED, client.connectionState.value)

        client.stop()
    }

    @Test
    fun connect_whenEndpointRecoversFromOffline_attemptsConnection() = testScope.runTest {
        var activeUrl = ""
        val client = ArcadeWebSocketClient(
            okHttpClient = okHttpClient,
            moshi = moshi,
            endpointUrlProvider = { activeUrl },
            scope = this,
            ioDispatcher = testDispatcher
        )

        client.start()
        assertEquals(WsConnectionStatus.DISCONNECTED, client.connectionState.value)

        // Simulate network recovery
        activeUrl = "http://100.111.123.93:8899"
        testScheduler.advanceTimeBy(1001L)

        // State moves to CONNECTING or RECONNECTING as URL is now valid
        assertTrue(
            client.connectionState.value == WsConnectionStatus.CONNECTING ||
            client.connectionState.value == WsConnectionStatus.RECONNECTING ||
            client.connectionState.value == WsConnectionStatus.FAILED
        )

        client.stop()
    }

    @Test
    fun stop_cancelsReconnectAndDisconnects() = testScope.runTest {
        val callCount = AtomicInteger(0)
        val client = ArcadeWebSocketClient(
            okHttpClient = okHttpClient,
            moshi = moshi,
            endpointUrlProvider = {
                callCount.incrementAndGet()
                ""
            },
            scope = this,
            ioDispatcher = testDispatcher
        )

        client.start()
        assertEquals(1, callCount.get())

        client.stop()
        assertEquals(WsConnectionStatus.DISCONNECTED, client.connectionState.value)

        // Advance time significantly; no further reconnect attempts should occur
        testScheduler.advanceTimeBy(60000L)
        assertEquals(1, callCount.get())
    }
}
