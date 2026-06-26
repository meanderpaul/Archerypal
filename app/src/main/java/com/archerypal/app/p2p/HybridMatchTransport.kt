package com.archerypal.app.p2p

import android.content.Context
import com.archerypal.app.data.DiscoveredHost
import com.archerypal.app.p2p.libp2p.Libp2pAdvertisement
import com.archerypal.app.p2p.libp2p.RelayReservationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonObject

class HybridMatchTransport(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val nearby = NearbyConnectionsManager(appContext)
    private val libp2p = Libp2pWanTransport()
    private val network = NetworkConnectivityMonitor(appContext)

    private val _events = MutableSharedFlow<P2PEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<P2PEvent> = _events

    private val _connectedEndpoints = MutableStateFlow<Set<String>>(emptySet())
    val connectedEndpoints: StateFlow<Set<String>> = _connectedEndpoints.asStateFlow()

    private val _activePaths = MutableStateFlow<Set<TransportPath>>(emptySet())
    val activePaths: StateFlow<Set<TransportPath>> = _activePaths.asStateFlow()

    private val _relayStatus = MutableStateFlow(RelayReservationStatus.WAITING)
    val relayStatus: StateFlow<RelayReservationStatus> = _relayStatus.asStateFlow()

    private var libp2pEnabledForSession = false
    private var hostingMatchId: String = ""
    private var hostingLocalName: String = ""
    private var joiningLocalName: String = ""
    private var pendingLibp2pJoin: DiscoveredHost? = null
    private var foregroundSyncActive = false

    init {
        nearby.events.onEach { _events.emit(it) }.launchIn(scope)
        libp2p.events.onEach { _events.emit(it) }.launchIn(scope)

        libp2p.relayStatus.onEach { status ->
            _relayStatus.value = status
            updateActivePaths(network.hasInternet.value)
        }.launchIn(scope)

        combine(nearby.connectedEndpoints, libp2p.connectedEndpoints) { local, remote ->
            local + remote
        }.onEach { endpoints ->
            _connectedEndpoints.value = endpoints
        }.launchIn(scope)

        network.hasInternet.onEach { online ->
            if (online && hostingMatchId.isNotBlank() && !libp2pEnabledForSession) {
                enableLibp2pHosting(hostingMatchId, hostingLocalName)
            } else if (!online && libp2pEnabledForSession) {
                disableLibp2p()
            }
            pendingLibp2pJoin?.let { host ->
                if (online && host.hasLibp2pRoute()) {
                    connectLibp2p(host, joiningLocalName)
                }
            }
            updateActivePaths(online)
        }.launchIn(scope)
    }

    fun startHosting(localName: String, matchId: String, onStarted: () -> Unit) {
        hostingMatchId = matchId
        hostingLocalName = localName
        pendingLibp2pJoin = null
        nearby.startHosting(localName, matchId, onStarted)
        if (network.hasInternet.value) {
            enableLibp2pHosting(matchId, localName)
        } else {
            updateActivePaths(false)
        }
    }

    fun startDiscovery(resyncHostName: String?, localName: String) {
        hostingMatchId = ""
        joiningLocalName = localName
        pendingLibp2pJoin = null
        nearby.startDiscovery()
        updateActivePaths(network.hasInternet.value)
    }

    fun connectToHost(host: DiscoveredHost, localName: String) {
        joiningLocalName = localName
        val wantsLibp2p = host.hasLibp2pRoute() ||
            host.endpointId == LIBP2P_HOST_ENDPOINT_ID ||
            host.endpointId.isBlank()

        if (wantsLibp2p && host.hasLibp2pRoute()) {
            if (network.hasInternet.value) {
                connectLibp2p(host, localName)
            } else {
                pendingLibp2pJoin = host
                _events.tryEmit(P2PEvent.Error("No cell or Wi‑Fi data — waiting for nearby host"))
            }
        }

        if (!isLibp2pEndpoint(host.endpointId) && host.endpointId.isNotBlank()) {
            nearby.connectToHost(host.endpointId, localName)
        }

        updateActivePaths(network.hasInternet.value)
    }

    fun sendMessage(endpointId: String, type: String, payload: JsonObject? = null) {
        when {
            isLibp2pEndpoint(endpointId) -> libp2p.sendMessage(endpointId, type, payload)
            else -> nearby.sendMessage(endpointId, type, payload)
        }
    }

    fun broadcast(type: String, payload: JsonObject? = null) {
        val localEndpoints = nearby.connectedEndpoints.value
        val libp2pEndpoints = libp2p.connectedEndpoints.value
        if (localEndpoints.isNotEmpty()) {
            localEndpoints.forEach { endpointId ->
                nearby.sendMessage(endpointId, type, payload)
            }
        }
        if (libp2pEndpoints.isNotEmpty() || (libp2pEnabledForSession && hostingMatchId.isNotBlank())) {
            libp2p.broadcast(type, payload)
        }
    }

    fun sendPlayerJoin(endpointId: String, playerName: String, matchId: String) {
        when {
            isLibp2pEndpoint(endpointId) ->
                libp2p.sendPlayerJoin(endpointId, playerName, matchId)
            else -> nearby.sendPlayerJoin(endpointId, playerName, matchId)
        }
    }

    fun libp2pAdvertisement(): Libp2pAdvertisement = libp2p.advertisement()

    fun shutdown() {
        hostingMatchId = ""
        pendingLibp2pJoin = null
        libp2pEnabledForSession = false
        stopForegroundSync()
        nearby.shutdown()
        libp2p.shutdown()
        _activePaths.value = emptySet()
        _relayStatus.value = RelayReservationStatus.WAITING
    }

    fun statusSuffix(): String {
        val paths = _activePaths.value
        val relay = _relayStatus.value
        val online = network.hasInternet.value
        return when {
            paths.contains(TransportPath.WAN) && paths.contains(TransportPath.LOCAL) ->
                when (relay) {
                    RelayReservationStatus.READY -> " · libp2p relay + nearby"
                    RelayReservationStatus.WAITING -> " · waiting for relay + nearby"
                    RelayReservationStatus.FAILED -> " · libp2p (relay failed) + nearby"
                }
            paths.contains(TransportPath.WAN) ->
                when (relay) {
                    RelayReservationStatus.READY -> " · libp2p relay ready"
                    RelayReservationStatus.WAITING -> " · waiting for relay"
                    RelayReservationStatus.FAILED -> " · libp2p (relay failed)"
                }
            online && libp2pEnabledForSession && relay == RelayReservationStatus.WAITING ->
                " · waiting for relay"
            online -> " · nearby (libp2p ready)"
            else -> " · nearby only"
        }
    }

    fun release() {
        shutdown()
        network.shutdown()
    }

    private fun enableLibp2pHosting(matchId: String, localName: String) {
        libp2p.startHosting(localName, matchId)
        libp2pEnabledForSession = true
        startForegroundSync()
        updateActivePaths(true)
    }

    private fun disableLibp2p() {
        libp2p.shutdown()
        libp2pEnabledForSession = false
        stopForegroundSync()
        updateActivePaths(false)
    }

    private fun connectLibp2p(host: DiscoveredHost, localName: String) {
        pendingLibp2pJoin = null
        libp2p.joinMatch(
            matchId = host.matchId,
            hostName = host.hostName,
            localName = localName,
            peerId = host.libp2pPeerId.orEmpty(),
            circuitMultiaddrs = host.libp2pCircuitMultiaddrs,
            directMultiaddrs = host.libp2pMultiaddrs
        )
        libp2pEnabledForSession = true
        startForegroundSync()
        updateActivePaths(network.hasInternet.value)
    }

    private fun startForegroundSync() {
        if (foregroundSyncActive) return
        foregroundSyncActive = true
        MatchSyncForegroundService.start(appContext)
    }

    private fun stopForegroundSync() {
        if (!foregroundSyncActive) return
        foregroundSyncActive = false
        MatchSyncForegroundService.stop(appContext)
    }

    private fun updateActivePaths(online: Boolean) {
        val paths = buildSet {
            add(TransportPath.LOCAL)
            if (online && libp2pEnabledForSession) {
                add(TransportPath.WAN)
            }
        }
        _activePaths.value = paths
    }
}

private fun DiscoveredHost.hasLibp2pRoute(): Boolean =
    !libp2pPeerId.isNullOrBlank() &&
        (libp2pCircuitMultiaddrs.isNotEmpty() || libp2pMultiaddrs.isNotEmpty())
