package com.example.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkMeshManagerRobolectricTest {

    @Test
    fun testConnectivityManagerRegistrationAndLifecycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = shadowOf(cm)

        val testScope = TestScope()
        val prober = NetworkMeshManagerTest.FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = true, latencyMs = 20L))
        }

        val manager = NetworkMeshManagerImpl(
            scope = testScope,
            socketProber = prober
        )

        manager.registerNetworkCallback(context)
        assertEquals(1, shadowCm.networkCallbacks.size)

        manager.unregisterNetworkCallback()
        assertEquals(0, shadowCm.networkCallbacks.size)
    }

    @Test
    fun testNetworkCallbackTriggersImmediateProbe() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowCm = shadowOf(cm)

        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        val prober = NetworkMeshManagerTest.FakeSocketProber().apply {
            setRule("100.111.123.93", ProbeResult(isReachable = true, latencyMs = 18L))
            setRule("192.168.1.161", ProbeResult(isReachable = true, latencyMs = 5L))
        }

        val manager = NetworkMeshManagerImpl(
            scope = testScope,
            ioDispatcher = testDispatcher,
            socketProber = prober
        )

        manager.registerNetworkCallback(context)
        manager.startMonitoring()
        testScope.runCurrent()

        assertEquals(ConnectionStatus.CONNECTED_TAILSCALE, manager.endpointState.value.status)

        // Simulate network drop
        val callback = shadowCm.networkCallbacks.first()
        prober.setRule("100.111.123.93", ProbeResult(isReachable = false, latencyMs = 1500L))
        prober.setRule("192.168.1.161", ProbeResult(isReachable = true, latencyMs = 6L))

        // Trigger onLost or onCapabilitiesChanged
        val network = cm.activeNetwork ?: cm.allNetworks.firstOrNull() ?: ShadowNetwork.newInstance(1)
        callback.onLost(network)
        testScope.runCurrent()

        assertEquals(ConnectionStatus.CONNECTED_LAN, manager.endpointState.value.status)
        manager.stopMonitoring()
        manager.close()
    }
}
