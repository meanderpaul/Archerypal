package com.archerypal.app.p2p

import com.archerypal.app.data.P2PMessage
import com.archerypal.app.p2p.libp2p.Libp2pAdvertisement
import com.archerypal.app.p2p.libp2p.Libp2pMatchNode
import com.archerypal.app.p2p.libp2p.Libp2pNodeEvent
import com.archerypal.app.p2p.libp2p.RelayReservationStatus
import io.libp2p.core.PeerId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class Libp2pWanTransport {

    private val node = Libp2pMatchNode(::handleNodeEvent)

    private val _events = MutableSharedFlow<P2PEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<P2PEvent> = _events.asSharedFlow()

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints.asStateFlow()

    private val _relayStatus = MutableStateFlow(RelayReservationStatus.WAITING)
    val relayStatus: StateFlow<RelayReservationStatus> = _relayStatus.asStateFlow()

    private var localName: String = ""
    private var matchId: String = ""
    private var isHost: Boolean = false
    private var hostPeerId: String? = null
    private var hostCircuitMultiaddrs: List<String> = emptyList()
    private var hostDirectMultiaddrs: List<String> = emptyList()
    private var remoteHostName: String = ""

    fun startHosting(localName: String, matchId: String) {
        this.localName = localName
        this.matchId = matchId
        this.isHost = true
        _relayStatus.value = RelayReservationStatus.WAITING
        node.startHosting()
    }

    fun joinMatch(
        matchId: String,
        hostName: String,
        localName: String,
        peerId: String,
        circuitMultiaddrs: List<String>,
        directMultiaddrs: List<String>
    ) {
        val dialAddrs = circuitMultiaddrs + directMultiaddrs
        if (peerId.isBlank() || dialAddrs.isEmpty()) {
            _events.tryEmit(P2PEvent.Error("Host libp2p address missing — scan QR or use nearby"))
            return
        }
        this.matchId = matchId
        this.localName = localName
        this.isHost = false
        this.hostPeerId = peerId
        this.hostCircuitMultiaddrs = circuitMultiaddrs
        this.hostDirectMultiaddrs = directMultiaddrs
        this.remoteHostName = hostName
        node.dial(peerId, circuitMultiaddrs, directMultiaddrs)
    }

    fun sendMessage(endpointId: String, type: String, payload: JsonObject?) {
        val message = P2PMessage(type = type, payload = payload)
        if (isHost) {
            val peerId = endpointToPeerId(endpointId) ?: return
            node.send(peerId, message)
        } else {
            val peerId = hostPeerId?.let { PeerId.fromBase58(it) } ?: return
            node.send(peerId, message)
        }
    }

    fun broadcast(type: String, payload: JsonObject?) {
        node.broadcast(P2PMessage(type = type, payload = payload))
    }

    fun sendPlayerJoin(endpointId: String, playerName: String, matchId: String) {
        sendMessage(
            endpointId,
            "PLAYER_JOIN",
            buildJsonObject {
                put("player_name", playerName)
                put("match_id", matchId)
            }
        )
    }

    fun shutdown() {
        node.stop()
        _connectedEndpoints.value = emptySet()
        _relayStatus.value = RelayReservationStatus.WAITING
        matchId = ""
        localName = ""
        isHost = false
        hostPeerId = null
        hostCircuitMultiaddrs = emptyList()
        hostDirectMultiaddrs = emptyList()
    }

    fun advertisement(): Libp2pAdvertisement = node.currentAdvertisement()

    private fun handleNodeEvent(event: Libp2pNodeEvent) {
        when (event) {
            is Libp2pNodeEvent.Started -> {
                hostPeerId = event.peerId
                hostCircuitMultiaddrs = event.circuitMultiaddrs
                hostDirectMultiaddrs = event.directMultiaddrs
                _relayStatus.value = event.relayStatus
                _events.tryEmit(P2PEvent.AdvertisingStarted)
            }
            is Libp2pNodeEvent.RelayUpdated -> {
                hostCircuitMultiaddrs = event.circuitMultiaddrs
                _relayStatus.value = event.relayStatus
                _events.tryEmit(P2PEvent.RelayStatusChanged(event.relayStatus))
            }
            is Libp2pNodeEvent.PeerConnected -> {
                val endpointId = if (isHost) {
                    peerEndpointId(event.peerId)
                } else {
                    LIBP2P_HOST_ENDPOINT_ID
                }
                _connectedEndpoints.value = _connectedEndpoints.value + endpointId
                val displayName = if (isHost) event.peerId.toBase58().take(8) else localName
                if (isHost) {
                    _events.tryEmit(P2PEvent.EndpointConnected(endpointId, displayName))
                } else {
                    _events.tryEmit(P2PEvent.EndpointConnected(LIBP2P_HOST_ENDPOINT_ID, remoteHostName))
                }
            }
            is Libp2pNodeEvent.PeerDisconnected -> {
                val endpointId = if (isHost) peerEndpointId(event.peerId) else LIBP2P_HOST_ENDPOINT_ID
                _connectedEndpoints.value = _connectedEndpoints.value - endpointId
                _events.tryEmit(P2PEvent.EndpointDisconnected(endpointId))
            }
            is Libp2pNodeEvent.MessageReceived -> {
                val endpointId = if (isHost) {
                    peerEndpointId(event.peerId)
                } else {
                    LIBP2P_HOST_ENDPOINT_ID
                }
                _events.tryEmit(P2PEvent.MessageReceived(endpointId, event.message))
            }
            is Libp2pNodeEvent.Error -> _events.tryEmit(P2PEvent.Error(event.message))
        }
    }

    private fun endpointToPeerId(endpointId: String): PeerId? {
        if (!endpointId.startsWith(LIBP2P_ENDPOINT_PREFIX)) return null
        return runCatching {
            PeerId.fromBase58(endpointId.removePrefix(LIBP2P_ENDPOINT_PREFIX))
        }.getOrNull()
    }
}

fun peerEndpointId(peerId: PeerId): String = LIBP2P_ENDPOINT_PREFIX + peerId.toBase58()
