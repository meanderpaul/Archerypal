package com.archerypal.app.data

import kotlinx.serialization.Serializable

enum class Trophy { GOLD, SILVER, BRONZE }

@Serializable
data class SavedFriend(
    val name: String,
    val lastHostName: String? = null,
    val lastMatchId: String? = null,
    val lastLibp2pPeerId: String? = null,
    val lastLibp2pCircuitMultiaddrs: List<String> = emptyList(),
    val lastLibp2pMultiaddrs: List<String> = emptyList(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

@Serializable
data class PlayerGlobalStats(
    val name: String,
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val totalPoints: Int = 0,
    val averagePointsPerMatch: Double = 0.0
)

@Serializable
data class SavedMatchRecord(
    val matchId: String,
    val completedAt: Long,
    val targetCount: Int,
    val scoringType: String = ScoringType.ASA.name,
    val playerNames: List<String>,
    val winnerName: String,
    val finalScores: Map<String, Int>
)

@Serializable
data class PersistedAppData(
    val playerName: String = "",
    val friends: List<SavedFriend> = emptyList(),
    val lastMatchGroup: List<String> = emptyList(),
    val lastHostName: String? = null,
    val globalStats: Map<String, PlayerGlobalStats> = emptyMap(),
    val matchHistory: List<SavedMatchRecord> = emptyList(),
    val isAdFree: Boolean = false
)

data class LeaderboardRow(
    val rank: Int,
    val name: String,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val trophy: Trophy? = null
)

fun computeAverage(totalPoints: Int, matchesPlayed: Int): Double =
    if (matchesPlayed == 0) 0.0 else totalPoints.toDouble() / matchesPlayed

fun rankGlobalStats(stats: Collection<PlayerGlobalStats>): List<PlayerGlobalStats> =
    stats.sortedWith(
        compareByDescending<PlayerGlobalStats> { it.matchesWon }
            .thenByDescending { it.averagePointsPerMatch }
            .thenByDescending { it.totalPoints }
    )

fun rankMatchTotals(totals: Map<String, Int>): List<Pair<String, Int>> =
    totals.entries.map { it.key to it.value }.sortedByDescending { it.second }

fun trophyForRank(rank: Int): Trophy? = when (rank) {
    1 -> Trophy.GOLD
    2 -> Trophy.SILVER
    3 -> Trophy.BRONZE
    else -> null
}

fun matchLeaderboardRows(totals: List<Pair<String, Int>>): List<LeaderboardRow> =
    totals.mapIndexed { index, (name, total) ->
        val rank = index + 1
        LeaderboardRow(
            rank = rank,
            name = name,
            primaryLabel = "$total pts",
            trophy = trophyForRank(rank)
        )
    }

fun globalLeaderboardRows(stats: List<PlayerGlobalStats>): List<LeaderboardRow> =
    stats.mapIndexed { index, player ->
        val rank = index + 1
        LeaderboardRow(
            rank = rank,
            name = player.name,
            primaryLabel = "${player.matchesWon} wins",
            secondaryLabel = buildString {
                append(String.format("%.1f", player.averagePointsPerMatch))
                append(" avg · ")
                append(player.matchesPlayed)
                append(" matches")
            },
            trophy = trophyForRank(rank)
        )
    }
