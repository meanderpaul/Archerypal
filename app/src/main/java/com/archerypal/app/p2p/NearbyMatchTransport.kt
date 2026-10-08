package com.archerypal.app.p2p

import android.content.Context
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** Wraps [NearbyConnectionsManager] behind [MatchTransport]. */
class NearbyMatchTransport(context: Context) : MatchTransport {

    private val nearby = NearbyConnectionsManager(context)

    override val events: SharedFlow<P2PEvent> = nearby.events
    override val connectedEndpoints: StateFlow<Set<String>> = nearby.connectedEndpoints

    override fun startHosting(localName: String, matchId: String, onStarted: () -> Unit) {
        nearby.startHosting(localName, matchId, onStarted)
    }

    override fun startDiscovery() = nearby.startDiscovery()

    override fun connectToHost(endpointId: String, localName: String) =
        nearby.connectToHost(endpointId, localName)

    override fun sendMessage(endpointId: String, type: String, payload: JsonObject?) =
        nearby.sendMessage(endpointId, type, payload)

    override fun broadcast(type: String, payload: JsonObject?) =
        nearby.broadcast(type, payload)

    override fun sendPlayerJoin(
        endpointId: String,
        playerName: String,
        matchId: String,
        friendCode: String?
    ) = nearby.sendPlayerJoin(endpointId, playerName, matchId, friendCode)

    override fun disconnectEndpoint(endpointId: String) =
        nearby.disconnectEndpoint(endpointId)

    override fun shutdown() = nearby.shutdown()
}
