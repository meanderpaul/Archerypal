package com.archerypal.app.p2p.libp2p

import com.archerypal.app.data.AppJson
import com.archerypal.app.data.P2PMessage
import io.libp2p.core.PeerId
import io.libp2p.core.Stream
import io.libp2p.core.multistream.ProtocolId
import io.libp2p.core.multistream.StrictProtocolBinding
import io.libp2p.etc.types.toByteBuf
import io.libp2p.protocol.ProtocolHandler
import io.libp2p.protocol.ProtocolMessageHandler
import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

interface MatchController {
    fun send(message: P2PMessage)
}

typealias OnMatchMessage = (PeerId, P2PMessage) -> Unit
typealias OnMatchPeerReady = (PeerId, MatchController) -> Unit

class Match(
    onMessage: OnMatchMessage,
    onPeerReady: OnMatchPeerReady
) : MatchBinding(MatchProtocol(onMessage, onPeerReady))

const val MATCH_PROTOCOL_ID: ProtocolId = "/archerypal/match/1.0.0"

open class MatchBinding(protocol: MatchProtocol) :
    StrictProtocolBinding<MatchController>(MATCH_PROTOCOL_ID, protocol)

open class MatchProtocol(
    private val onMessage: OnMatchMessage,
    private val onPeerReady: OnMatchPeerReady
) : ProtocolHandler<MatchController>(Long.MAX_VALUE, Long.MAX_VALUE) {

    override fun onStartInitiator(stream: Stream): CompletableFuture<MatchController> = onStart(stream)
    override fun onStartResponder(stream: Stream): CompletableFuture<MatchController> = onStart(stream)

    private fun onStart(stream: Stream): CompletableFuture<MatchController> {
        val ready = CompletableFuture<MatchController>()
        val handler = MatchHandler(onMessage, onPeerReady, ready)
        stream.pushHandler(handler)
        return ready.thenApply { handler }
    }

    inner class MatchHandler(
        private val onMessage: OnMatchMessage,
        private val onPeerReady: OnMatchPeerReady,
        val ready: CompletableFuture<MatchController>
    ) : ProtocolMessageHandler<ByteBuf>, MatchController {
        lateinit var stream: Stream

        override fun onActivated(stream: Stream) {
            this.stream = stream
            ready.complete(this)
            onPeerReady(stream.remotePeerId(), this)
        }

        override fun onMessage(stream: Stream, msg: ByteBuf) {
            val raw = msg.toString(StandardCharsets.UTF_8)
            runCatching {
                AppJson.decodeFromString(P2PMessage.serializer(), raw)
            }.onSuccess { message ->
                onMessage(stream.remotePeerId(), message)
            }
        }

        override fun send(message: P2PMessage) {
            val data = AppJson.encodeToString(P2PMessage.serializer(), message)
            stream.writeAndFlush(data.toByteArray(StandardCharsets.UTF_8).toByteBuf())
        }
    }
}
