package com.archerypal.app.p2p.libp2p

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.protocol.circuit.RelayTransport

/**
 * Curated public libp2p nodes that accept circuit relay v2 reservations.
 * These are community infrastructure (e.g. libp2p/IPFS bootstrap nodes), not servers we operate.
 * TCP /ip4 addresses only — jvm-libp2p does not dial dnsaddr.
 */
data class PublicRelay(
    val name: String,
    val peerIdBase58: String,
    val dialAddresses: List<String>
) {
    val peerId: PeerId get() = PeerId.fromBase58(peerIdBase58)
}

object Libp2pRelayConfig {
    const val TARGET_RELAY_COUNT = 2
    const val RESERVE_POLL_INTERVAL_MS = 5_000L
    const val RESERVE_MAX_WAIT_MS = 120_000L
    const val RECONNECT_DELAY_SECONDS = 5L

    val publicRelays: List<PublicRelay> = listOf(
        PublicRelay(
            name = "libp2p-bootstrap-1",
            peerIdBase58 = "QmNnooDu7bfjPFqTUXjVWizdmVEg36DiaQxgFFA9GmseQm",
            dialAddresses = listOf(
                "/ip4/104.131.131.82/tcp/4001/p2p/QmNnooDu7bfjPFqTUXjVWizdmVEg36DiaQxgFFA9GmseQm"
            )
        ),
        PublicRelay(
            name = "libp2p-bootstrap-2",
            peerIdBase58 = "QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa",
            dialAddresses = listOf(
                "/ip4/104.236.179.41/tcp/4001/p2p/QmQCU2EcMqAqQPR2i9bChDtGNJchTbq5TbXJJ16u19uLTa"
            )
        ),
        PublicRelay(
            name = "ipfs-bootstrap",
            peerIdBase58 = "QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ",
            dialAddresses = listOf(
                "/ip4/104.131.131.82/tcp/4001/p2p/QmaCpDMGvV2BGHeYERUEnRQAwe3N8SzbUtfsmvsqQLuvuJ"
            )
        )
    )

    fun candidateRelays(@Suppress("UNUSED_PARAMETER") host: io.libp2p.core.Host): List<RelayTransport.CandidateRelay> =
        publicRelays.map { relay ->
            RelayTransport.CandidateRelay(
                relay.peerId,
                relay.dialAddresses.map { Multiaddr.fromString(it) }
            )
        }

    fun buildCircuitAddresses(hostPeerIdBase58: String, reservedRelayPeerIds: Collection<String>): List<String> {
        val relaysById = publicRelays.associateBy { it.peerIdBase58 }
        return reservedRelayPeerIds.flatMap { relayId ->
            val relay = relaysById[relayId] ?: return@flatMap emptyList()
            relay.dialAddresses.map { dialAddr ->
                "$dialAddr/p2p-circuit/p2p/$hostPeerIdBase58"
            }
        }
    }
}
