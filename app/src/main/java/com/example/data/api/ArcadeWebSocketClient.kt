package com.example.data.api

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicBoolean

enum class WsConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED
}

class ArcadeWebSocketClient(
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi,
    private val endpointUrlProvider: () -> String,
    private val scope: CoroutineScope
) {
    private val _connectionState = MutableStateFlow(WsConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionStatus> = _connectionState.asStateFlow()

    private val _telemetryFlow = MutableStateFlow<TelemetryResponse?>(null)
    val telemetryFlow: StateFlow<TelemetryResponse?> = _telemetryFlow.asStateFlow()

    private val _eventFlow = MutableSharedFlow<ArcadeEventBusMessage>(extraBufferCapacity = 64)
    val eventFlow: SharedFlow<ArcadeEventBusMessage> = _eventFlow.asSharedFlow()

    private var activeWebSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private val shouldReconnect = AtomicBoolean(true)
    private var backoffMs = 1000L
    private val maxBackoffMs = 30000L

    private val telemetryAdapter = moshi.adapter(TelemetryResponse::class.java)
    private val eventMessageAdapter = moshi.adapter(ArcadeEventBusMessage::class.java)

    fun start() {
        shouldReconnect.set(true)
        connect()
    }

    fun stop() {
        shouldReconnect.set(false)
        reconnectJob?.cancel()
        activeWebSocket?.close(1000, "Client shutdown")
        activeWebSocket = null
        _connectionState.value = WsConnectionStatus.DISCONNECTED
    }

    private fun connect() {
        if (_connectionState.value == WsConnectionStatus.CONNECTED ||
            _connectionState.value == WsConnectionStatus.CONNECTING) return

        _connectionState.value = if (backoffMs > 1000L) WsConnectionStatus.RECONNECTING else WsConnectionStatus.CONNECTING

        val baseUrl = endpointUrlProvider().trim()
        val wsUrl = if (baseUrl.startsWith("http://")) {
            baseUrl.replace("http://", "ws://").trimEnd('/') + "/ws"
        } else if (baseUrl.startsWith("https://")) {
            baseUrl.replace("https://", "wss://").trimEnd('/') + "/ws"
        } else {
            "ws://$baseUrl:8899/ws"
        }

        val request = Request.Builder().url(wsUrl).build()
        activeWebSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _connectionState.value = WsConnectionStatus.CONNECTED
            backoffMs = 1000L // Reset backoff upon successful connection
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            parseMessage(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = WsConnectionStatus.DISCONNECTED
            triggerReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.value = WsConnectionStatus.FAILED
            triggerReconnect()
        }
    }

    private fun parseMessage(jsonText: String) {
        try {
            val root = org.json.JSONObject(jsonText)
            val type = root.optString("type")
            val dataObj = root.optJSONObject("data")
            if (dataObj != null) {
                val dataJson = dataObj.toString()
                if (type == "telemetry") {
                    val telem = telemetryAdapter.fromJson(dataJson)
                    if (telem != null) {
                        _telemetryFlow.value = telem
                    }
                } else if (type == "event") {
                    val event = eventMessageAdapter.fromJson(dataJson)
                    if (event != null) {
                        scope.launch { _eventFlow.emit(event) }
                    }
                }
            }
        } catch (e: Exception) {
            // Gracefully ignore corrupt frame
        }
    }

    private fun triggerReconnect() {
        if (!shouldReconnect.get()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
            connect()
        }
    }
}
