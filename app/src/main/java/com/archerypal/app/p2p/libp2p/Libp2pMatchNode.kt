package com.archerypal.app.p2p.libp2p

import com.archerypal.app.data.P2PMessage
import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.dsl.HostBuilder
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.protocol.circuit.CircuitHopProtocol
import io.libp2p.protocol.circuit.CircuitStopProtocol
import io.libp2p.protocol.circuit.RelayTransport
import io.libp2p.transport.ConnectionUpgrader
import io.libp2p.transport.tcp.TcpTransport
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class RelayReservationStatus {
    WAITING,
    READY,
    FAILED
}

data class Libp2pAdvertisement(
    val peerId: String?,
    val circuitMultiaddrs: List<String>,
    val directMultiaddrs: List<String>,
    val relayStatus: RelayReservationStatus
)

sealed class Libp2pNodeEvent {
    data class Started(
        val peerId: String,
        val directMultiaddrs: List<String>,
        val circuitMultiaddrs: List<String> = emptyList(),
        val relayStatus: RelayReservationStatus = RelayReservationStatus.WAITING
    ) : Libp2pNodeEvent()

    data class RelayUpdated(
        val circuitMultiaddrs: List<String>,
        val relayStatus: RelayReservationStatus
    ) : Libp2pNodeEvent()

    data class PeerConnected(val peerId: PeerId, val controller: MatchController) : Libp2pNodeEvent()
    data class PeerDisconnected(val peerId: PeerId) : Libp2pNodeEvent()
    data class MessageReceived(val peerId: PeerId, val message: P2PMessage) : Libp2pNodeEvent()
    data class Error(val message: String) : Libp2pNodeEvent()
}

class Libp2pMatchNode(
    private val onEvent: (Libp2pNodeEvent) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "libp2p-match").apply { isDaemon = true }
    }
    private val relayScheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "libp2p-relay").apply { isDaemon = true }
    }
    private val running = AtomicBoolean(false)
    private var isHosting = false
    private var matchHost: Host? = null
    private var relayTransport: RelayTransport? = null
    private var hopBinding: CircuitHopProtocol.Binding? = null
    private val controllers = ConcurrentHashMap<PeerId, MatchController>()

    @Volatile
    private var circuitMultiaddrs: List<String> = emptyList()

    @Volatile
    private var relayStatus: RelayReservationStatus = RelayReservationStatus.WAITING

    private var lastDialPeerId: String? = null
    private var lastDialCircuitAddrs: List<String> = emptyList()
    private var lastDialDirectAddrs: List<String> = emptyList()

    private val clientOnlyRelayManager = object : CircuitHopProtocol.RelayManager {
        override fun hasReservation(source: PeerId): Boolean = false

        override fun createReservation(requestor: PeerId, addr: Multiaddr): Optional<CircuitHopProtocol.Reservation> =
            Optional.empty()

        override fun allowConnection(target: PeerId, initiator: PeerId): Optional<CircuitHopProtocol.Reservation> =
            Optional.empty()
    }

    fun startHosting() {
        executor.execute {
            if (running.getAndSet(true)) return@execute
            isHosting = true
            runCatching {
                val node = buildHost()
                matchHost = node
                node.start().get()
                relayTransport?.setRelayCount(Libp2pRelayConfig.TARGET_RELAY_COUNT)
                val peerId = node.peerId.toBase58()
                val directAddrs = directAdvertisedAddresses(node)
                onEvent(
                    Libp2pNodeEvent.Started(
                        peerId = peerId,
                        directMultiaddrs = directAddrs,
                        circuitMultiaddrs = emptyList(),
                        relayStatus = RelayReservationStatus.WAITING
                    )
                )
                startRelayReservation(peerId)
            }.onFailure { error ->
                running.set(false)
                isHosting = false
                onEvent(Libp2pNodeEvent.Error("libp2p host failed: ${error.message}"))
            }
        }
    }

    fun dial(peerIdBase58: String, circuitMultiaddrs: List<String>, directMultiaddrs: List<String>) {
        executor.execute {
            lastDialPeerId = peerIdBase58
            lastDialCircuitAddrs = circuitMultiaddrs
            lastDialDirectAddrs = directMultiaddrs
            dialOrdered(peerIdBase58, circuitMultiaddrs + directMultiaddrs)
        }
    }

    fun send(peerId: PeerId, message: P2PMessage) {
        executor.execute {
            controllers[peerId]?.send(message)
        }
    }

    fun broadcast(message: P2PMessage) {
        executor.execute {
            controllers.values.forEach { it.send(message) }
        }
    }

    fun stop() {
        executor.execute {
            if (!running.getAndSet(false)) return@execute
            isHosting = false
            lastDialPeerId = null
            lastDialCircuitAddrs = emptyList()
            lastDialDirectAddrs = emptyList()
            circuitMultiaddrs = emptyList()
            relayStatus = RelayReservationStatus.WAITING
            controllers.clear()
            runCatching { matchHost?.stop()?.get() }
            matchHost = null
            relayTransport = null
            hopBinding = null
        }
    }

    fun currentAdvertisement(): Libp2pAdvertisement {
        val host = matchHost
        return if (host == null) {
            Libp2pAdvertisement(null, emptyList(), emptyList(), RelayReservationStatus.WAITING)
        } else {
            Libp2pAdvertisement(
                peerId = host.peerId.toBase58(),
                circuitMultiaddrs = circuitMultiaddrs,
                directMultiaddrs = directAdvertisedAddresses(host),
                relayStatus = relayStatus
            )
        }
    }

    private fun dialOrdered(peerIdBase58: String, multiaddrs: List<String>) {
        runCatching {
            ensureHostStarted()
            val host = matchHost ?: error("libp2p host not running")
            val peerId = PeerId.fromBase58(peerIdBase58)
            if (controllers.containsKey(peerId)) return

            if (multiaddrs.isEmpty()) {
                onEvent(Libp2pNodeEvent.Error("Host libp2p address missing — scan QR or use nearby"))
                return
            }

            var lastError: Throwable? = null
            for (addr in multiaddrs) {
                runCatching {
                    val multiaddr = Multiaddr.fromString(addr)
                    val binding = Match(::handleMessage, ::registerPeer)
                    val session = binding.dial(host, peerId, multiaddr)
                    val controller = session.controller.get()
                    val stream = session.stream.get()
                    registerPeer(peerId, controller)
                    stream.closeFuture().thenAccept {
                        unregisterPeer(peerId)
                    }
                    return
                }.onFailure { lastError = it }
            }
            onEvent(
                Libp2pNodeEvent.Error(
                    "Could not reach host over libp2p: ${lastError?.message ?: "no route"}"
                )
            )
        }.onFailure { error ->
            onEvent(Libp2pNodeEvent.Error("libp2p dial failed: ${error.message}"))
        }
    }

    private fun ensureHostStarted() {
        if (running.get()) return
        running.set(true)
        isHosting = false
        val node = buildHost()
        matchHost = node
        node.start().get()
        relayTransport?.setRelayCount(Libp2pRelayConfig.TARGET_RELAY_COUNT)
    }

    private fun buildHost(): Host {
        val stopBinding = CircuitStopProtocol.Binding(CircuitStopProtocol())
        val hop = CircuitHopProtocol.Binding(clientOnlyRelayManager, stopBinding)
        hopBinding = hop

        val host = HostBuilder()
            .protocol(
                Match(::handleMessage, ::registerPeer),
                hop,
                stopBinding
            )
            .transport(
                { upgrader: ConnectionUpgrader -> TcpTransport(upgrader) },
                { upgrader: ConnectionUpgrader ->
                    RelayTransport(
                        hop,
                        stopBinding,
                        upgrader,
                        { h -> Libp2pRelayConfig.candidateRelays(h) },
                        relayScheduler
                    ).also { transport ->
                        transport.setRelayCount(Libp2pRelayConfig.TARGET_RELAY_COUNT)
                        relayTransport = transport
                    }
                }
            )
            .listen("/ip4/0.0.0.0/tcp/0")
            .build()

        return host
    }

    private fun startRelayReservation(hostPeerId: String) {
        relayScheduler.execute {
            val deadline = System.currentTimeMillis() + Libp2pRelayConfig.RESERVE_MAX_WAIT_MS
            val reserved = linkedSetOf<String>()
            val hop = hopBinding

            while (running.get() &&
                reserved.size < Libp2pRelayConfig.TARGET_RELAY_COUNT &&
                System.currentTimeMillis() < deadline
            ) {
                relayTransport?.ensureEnoughCurrentRelays()

                if (hop != null) {
                    for (relay in Libp2pRelayConfig.publicRelays) {
                        if (relay.peerIdBase58 in reserved) continue
                        runCatching {
                            val host = matchHost ?: return@execute
                            val addrs = relay.dialAddresses.map { Multiaddr.fromString(it) }.toTypedArray()
                            val controller = hop.dial(host, relay.peerId, *addrs).controller.get()
                            controller.reserve().get()
                            reserved.add(relay.peerIdBase58)
                            if (reserved.size >= Libp2pRelayConfig.TARGET_RELAY_COUNT) break
                        }
                    }
                }

                if (reserved.size >= Libp2pRelayConfig.TARGET_RELAY_COUNT) break
                Thread.sleep(Libp2pRelayConfig.RESERVE_POLL_INTERVAL_MS)
            }

            val status = if (reserved.isNotEmpty()) {
                RelayReservationStatus.READY
            } else {
                RelayReservationStatus.FAILED
            }
            val addrs = Libp2pRelayConfig.buildCircuitAddresses(hostPeerId, reserved)
            circuitMultiaddrs = addrs
            relayStatus = status
            onEvent(Libp2pNodeEvent.RelayUpdated(addrs, status))
        }
    }

    private fun registerPeer(peerId: PeerId, controller: MatchController) {
        if (controllers.putIfAbsent(peerId, controller) == null) {
            onEvent(Libp2pNodeEvent.PeerConnected(peerId, controller))
        }
    }

    private fun unregisterPeer(peerId: PeerId) {
        if (controllers.remove(peerId) != null) {
            onEvent(Libp2pNodeEvent.PeerDisconnected(peerId))
            if (!isHosting && lastDialPeerId != null) {
                scheduleReconnect()
            }
        }
    }

    private fun scheduleReconnect() {
        val peerId = lastDialPeerId ?: return
        val circuit = lastDialCircuitAddrs
        val direct = lastDialDirectAddrs
        relayScheduler.schedule({
            executor.execute {
                if (!running.get() || isHosting) return@execute
                val target = runCatching { PeerId.fromBase58(peerId) }.getOrNull() ?: return@execute
                if (controllers.containsKey(target)) return@execute
                dialOrdered(peerId, circuit + direct)
            }
        }, Libp2pRelayConfig.RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS)
    }

    private fun handleMessage(peerId: PeerId, message: P2PMessage) {
        onEvent(Libp2pNodeEvent.MessageReceived(peerId, message))
    }

    private fun directAdvertisedAddresses(node: Host): List<String> =
        node.listenAddresses()
            .map { it.toString() }
            .filter { addr ->
                !addr.contains("/ip4/127.0.0.1/") && !addr.contains("/ip4/0.0.0.0/")
            }
            .ifEmpty { node.listenAddresses().map { it.toString() } }
}
