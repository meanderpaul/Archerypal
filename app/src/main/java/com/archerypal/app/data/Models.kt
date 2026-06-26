package com.archerypal.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object AppConstants {
    const val SERVICE_ID = "com.archerypal.app.nearby"
    const val QR_PREFIX = "archerypal://join?"
    const val MIN_TARGETS = 1
    const val MAX_TARGETS = 60
    const val MIN_SCORE = 0
    const val FREEFORM_MAX_SCORE = 120
}

enum class ScoringType(val label: String, val description: String) {
    ASA(
        label = "ASA",
        description = "12 · 10 · 8 · 5 · 0 per target"
    ),
    IBO(
        label = "IBO",
        description = "11 · 10 · 8 · 5 · 0 per target"
    ),
    UNIVERSAL(
        label = "Universal",
        description = "14 · 12 · 11 · 10 · 8 · 5 · 0 per target"
    ),
    FREEFORM(
        label = "Freeform",
        description = "Enter any score from 0 to 120 per target"
    );

    val allowedScores: List<Int>? get() = when (this) {
        ASA -> listOf(12, 10, 8, 5, 0)
        IBO -> listOf(11, 10, 8, 5, 0)
        UNIVERSAL -> listOf(14, 12, 11, 10, 8, 5, 0)
        FREEFORM -> null
    }

    fun isValidScore(value: Int): Boolean = when (this) {
        FREEFORM -> value in AppConstants.MIN_SCORE..AppConstants.FREEFORM_MAX_SCORE
        else -> value in (allowedScores ?: emptyList())
    }

    fun scoreInputMaxDigits(): Int = when (this) {
        FREEFORM -> 3
        else -> 2
    }
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
    val scoringType: String = ScoringType.ASA.name,
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
    val serviceId: String = AppConstants.SERVICE_ID,
    val libp2pPeerId: String? = null,
    val libp2pCircuitMultiaddrs: List<String> = emptyList(),
    val libp2pMultiaddrs: List<String> = emptyList()
)

@Serializable
data class DiscoveredHost(
    val endpointId: String,
    val hostName: String,
    val matchId: String,
    val libp2pPeerId: String? = null,
    val libp2pCircuitMultiaddrs: List<String> = emptyList(),
    val libp2pMultiaddrs: List<String> = emptyList()
)

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun parseScoringType(raw: String?): ScoringType = when (raw) {
    "REFORM" -> ScoringType.FREEFORM // legacy name
    else -> raw?.let { runCatching { ScoringType.valueOf(it) }.getOrNull() } ?: ScoringType.ASA
}

fun generateMatchId(): String = "match_${System.currentTimeMillis().toString(36)}"

fun scoreKey(playerName: String, targetIndex: Int): String = "$playerName#$targetIndex"
