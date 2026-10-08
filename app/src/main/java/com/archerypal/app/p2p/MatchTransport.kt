package com.archerypal.app.p2p

import com.archerypal.app.data.P2PMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** Shared transport contract for Nearby (local) and server relay (remote). */
interface MatchTransport {
    val events: SharedFlow<P2PEvent>
    val connectedEndpoints: StateFlow<Set<String>>

    fun startHosting(localName: String, matchId: String, onStarted: () -> Unit = {})
    fun startDiscovery()
    fun connectToHost(endpointId: String, localName: String)
    fun sendMessage(endpointId: String, type: String, payload: JsonObject? = null)
    fun broadcast(type: String, payload: JsonObject? = null)
    fun sendPlayerJoin(endpointId: String, playerName: String, matchId: String, friendCode: String? = null)
    fun disconnectEndpoint(endpointId: String)
    fun shutdown()
}

object RemoteEndpoints {
    const val HOST = "remote:host"
}
