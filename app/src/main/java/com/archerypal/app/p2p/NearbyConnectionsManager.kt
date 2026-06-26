package com.archerypal.app.p2p

import android.content.Context
import com.archerypal.app.data.AppConstants
import com.archerypal.app.data.AppJson
import com.archerypal.app.data.DiscoveredHost
import com.archerypal.app.data.P2PMessage
import com.archerypal.app.p2p.libp2p.RelayReservationStatus
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

sealed class P2PEvent {
    data class EndpointConnected(val endpointId: String, val name: String) : P2PEvent()
    data class EndpointDisconnected(val endpointId: String) : P2PEvent()
    data class MessageReceived(val endpointId: String, val message: P2PMessage) : P2PEvent()
    data class HostDiscovered(val host: DiscoveredHost) : P2PEvent()
    data class Error(val message: String) : P2PEvent()
    data class RelayStatusChanged(val status: RelayReservationStatus) : P2PEvent()
    data object AdvertisingStarted : P2PEvent()
    data object DiscoveryStarted : P2PEvent()
}

class NearbyConnectionsManager(context: Context) {

    private val appContext = context.applicationContext
    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(appContext)
    private val strategy = Strategy.P2P_CLUSTER

    private val _events = MutableSharedFlow<P2PEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<P2PEvent> = _events.asSharedFlow()

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints.asStateFlow()

    private var localName: String = ""
    private var isHost: Boolean = false
    private val endpointDisplayNames = mutableMapOf<String, String>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            runCatching {
                AppJson.decodeFromString(P2PMessage.serializer(), String(bytes, Charsets.UTF_8))
            }.onSuccess { message ->
                _events.tryEmit(P2PEvent.MessageReceived(endpointId, message))
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            endpointDisplayNames[endpointId] = info.endpointName.substringBefore('|')
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    _connectedEndpoints.value = _connectedEndpoints.value + endpointId
                    val name = endpointDisplayNames[endpointId] ?: endpointId
                    _events.tryEmit(P2PEvent.EndpointConnected(endpointId, name))
                }
                else -> _events.tryEmit(P2PEvent.Error("Connection failed: ${result.status.statusCode}"))
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedEndpoints.value = _connectedEndpoints.value - endpointId
            endpointDisplayNames.remove(endpointId)
            _events.tryEmit(P2PEvent.EndpointDisconnected(endpointId))
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val hostName = info.endpointName.substringBefore('|')
            val matchId = info.endpointName.substringAfter('|', "")
            if (matchId.isNotBlank()) {
                _events.tryEmit(
                    P2PEvent.HostDiscovered(
                        DiscoveredHost(endpointId, hostName, matchId)
                    )
                )
            }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    fun startHosting(localName: String, matchId: String, onStarted: () -> Unit) {
        this.localName = localName
        this.isHost = true
        val endpointName = "$localName|$matchId"
        val options = AdvertisingOptions.Builder().setStrategy(strategy).build()

        connectionsClient.startAdvertising(
            endpointName,
            AppConstants.SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            _events.tryEmit(P2PEvent.AdvertisingStarted)
            onStarted()
        }.addOnFailureListener { e ->
            _events.tryEmit(P2PEvent.Error("Advertising failed: ${e.message}"))
        }
    }

    fun startDiscovery() {
        isHost = false
        val options = DiscoveryOptions.Builder().setStrategy(strategy).build()
        connectionsClient.startDiscovery(
            AppConstants.SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            _events.tryEmit(P2PEvent.DiscoveryStarted)
        }.addOnFailureListener { e ->
            _events.tryEmit(P2PEvent.Error("Discovery failed: ${e.message}"))
        }
    }

    fun connectToHost(endpointId: String, localName: String) {
        this.localName = localName
        connectionsClient.requestConnection(localName, endpointId, connectionLifecycleCallback)
            .addOnFailureListener { e ->
                _events.tryEmit(P2PEvent.Error("Connect failed: ${e.message}"))
            }
    }

    fun sendMessage(endpointId: String, type: String, payload: JsonObject? = null) {
        val message = P2PMessage(type = type, payload = payload)
        val bytes = AppJson.encodeToString(P2PMessage.serializer(), message).toByteArray(Charsets.UTF_8)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    fun broadcast(type: String, payload: JsonObject? = null) {
        _connectedEndpoints.value.forEach { endpointId ->
            sendMessage(endpointId, type, payload)
        }
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
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        _connectedEndpoints.value = emptySet()
    }
}
