package com.archerypal.app.p2p

enum class TransportPath(val label: String) {
    LOCAL("Nearby"),
    WAN("libp2p")
}

fun isLibp2pEndpoint(endpointId: String): Boolean =
    endpointId.startsWith(LIBP2P_ENDPOINT_PREFIX) || endpointId == LIBP2P_HOST_ENDPOINT_ID

const val LIBP2P_ENDPOINT_PREFIX = "libp2p:"
const val LIBP2P_HOST_ENDPOINT_ID = "libp2p:host"
