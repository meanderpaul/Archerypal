package com.archerypal.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object AppConstants {
    const val SERVICE_ID = "com.archerypal.nearby"
    const val QR_PREFIX = "archerypal://join?"
    const val MIN_TARGETS = 1
    const val MAX_TARGETS = 60
    const val MIN_SCORE = 0
    const val MAX_SCORE = 10
}

enum class MatchPhase {
    LOBBY,
    SETUP,
    SCORING,
    FINISHED
}

enum class MessageType {
    PLAYER_JOIN,
    MATCH_SETUP,
    SCORE_SUBMIT,
    MATCH_STATE,
    SCORE_ACK,
    REQUEST_SYNC,
    MATCH_FINISHED
}

@Serializable
data class ScoreEntry(
    val matchId: String,
    val playerName: String,
    val targetIndex: Int,
    val scoreValue: Int
)

@Serializable
data class PlayerInfo(
    val name: String,
    val endpointId: String,
    val isHost: Boolean = false
)

@Serializable
data class MatchState(
    val matchId: String,
    val phase: String = MatchPhase.LOBBY.name,
    val hostName: String = "",
    val targetCount: Int = 0,
    val players: List<PlayerInfo> = emptyList(),
    val scores: List<ScoreEntry> = emptyList()
)

@Serializable
data class P2PMessage(
    val type: String,
    val payload: kotlinx.serialization.json.JsonObject? = null
)

@Serializable
data class QrPayload(
    val matchId: String,
    val hostName: String,
    val serviceId: String = AppConstants.SERVICE_ID
)

@Serializable
data class DiscoveredHost(
    val endpointId: String,
    val hostName: String,
    val matchId: String
)

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun generateMatchId(): String = "match_${System.currentTimeMillis().toString(36)}"

fun scoreKey(playerName: String, targetIndex: Int): String = "$playerName#$targetIndex"
