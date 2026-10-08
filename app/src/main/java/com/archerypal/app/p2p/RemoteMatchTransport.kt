package com.archerypal.app.p2p

import com.archerypal.app.data.AppJson
import com.archerypal.app.data.P2PMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Match sync over the Pi relay WebSocket. The server forwards JSON [P2PMessage] frames
 * between everyone in the room (host-authoritative logic stays in [MatchViewModel]).
 */
class RemoteMatchTransport(
    private val isHost: Boolean,
    private val localName: String
) : MatchTransport {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var hostDisplayName: String = localName

    private val _events = MutableSharedFlow<P2PEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<P2PEvent> = _events.asSharedFlow()

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    override val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints.asStateFlow()

    fun connect(wsUrl: String, joinToken: String, remoteHostName: String = "Host") {
        hostDisplayName = remoteHostName
        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer $joinToken")
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isHost) {
                    _connectedEndpoints.value = emptySet()
                    _events.tryEmit(P2PEvent.AdvertisingStarted)
                } else {
                    _connectedEndpoints.value = setOf(RemoteEndpoints.HOST)
                    _events.tryEmit(
                        P2PEvent.EndpointConnected(RemoteEndpoints.HOST, hostDisplayName)
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    AppJson.decodeFromString(P2PMessage.serializer(), text)
                }.onSuccess { message ->
                    val endpointId = if (isHost) {
                        // Host attributes inbound messages to the remote peer slot.
                        connectedEndpoints.value.firstOrNull() ?: "remote:peer"
                    } else {
                        RemoteEndpoints.HOST
                    }
                    if (isHost && message.type == "PLAYER_JOIN") {
                        _connectedEndpoints.value = setOf("remote:peer")
                    }
                    _events.tryEmit(P2PEvent.MessageReceived(endpointId, message))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _events.tryEmit(P2PEvent.Error(t.message ?: "WebSocket connection failed"))
                handleDisconnect()
            }
        })
    }

    private fun handleDisconnect() {
        val endpoints = _connectedEndpoints.value
        _connectedEndpoints.value = emptySet()
        endpoints.forEach { _events.tryEmit(P2PEvent.EndpointDisconnected(it)) }
    }

    override fun startHosting(localName: String, matchId: String, onStarted: () -> Unit) {
        onStarted()
    }

    override fun startDiscovery() = Unit

    override fun connectToHost(endpointId: String, localName: String) = Unit

    override fun sendMessage(endpointId: String, type: String, payload: JsonObject?) {
        sendFrame(P2PMessage(type = type, payload = payload))
    }

    override fun broadcast(type: String, payload: JsonObject?) {
        sendFrame(P2PMessage(type = type, payload = payload))
    }

    override fun sendPlayerJoin(
        endpointId: String,
        playerName: String,
        matchId: String,
        friendCode: String?
    ) {
        sendMessage(
            endpointId,
            "PLAYER_JOIN",
            buildJsonObject {
                put("player_name", playerName)
                put("match_id", matchId)
                if (!friendCode.isNullOrBlank()) {
                    put("friend_code", friendCode)
                }
            }
        )
    }

    private fun sendFrame(message: P2PMessage) {
        val json = AppJson.encodeToString(P2PMessage.serializer(), message)
        webSocket?.send(json)
    }

    override fun disconnectEndpoint(endpointId: String) {
        webSocket?.close(1000, "leave")
        webSocket = null
        handleDisconnect()
    }

    override fun shutdown() {
        webSocket?.close(1000, "shutdown")
        webSocket = null
        _connectedEndpoints.value = emptySet()
    }
}
