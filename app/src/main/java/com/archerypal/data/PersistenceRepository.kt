package com.archerypal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "archerypal")

class PersistenceRepository(private val context: Context) {

    private val dataKey = stringPreferencesKey("app_data_json")

    val dataFlow: Flow<PersistedAppData> = context.appDataStore.data.map { prefs ->
        val raw = prefs[dataKey]
        if (raw.isNullOrBlank()) {
            PersistedAppData()
        } else {
            runCatching {
                AppJson.decodeFromString(PersistedAppData.serializer(), raw)
            }.getOrDefault(PersistedAppData())
        }
    }

    suspend fun update(transform: (PersistedAppData) -> PersistedAppData) {
        context.appDataStore.edit { prefs ->
            val current = prefs[dataKey]?.let {
                runCatching { AppJson.decodeFromString(PersistedAppData.serializer(), it) }
                    .getOrDefault(PersistedAppData())
            } ?: PersistedAppData()
            prefs[dataKey] = AppJson.encodeToString(
                PersistedAppData.serializer(),
                transform(current)
            )
        }
    }

    suspend fun savePlayerName(name: String) {
        update { it.copy(playerName = name.trim()) }
    }

    suspend fun rememberFriend(name: String, hostName: String?, matchId: String?) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        update { data ->
            val others = data.friends.filterNot { it.name.equals(trimmed, ignoreCase = true) }
            val friend = SavedFriend(
                name = trimmed,
                lastHostName = hostName,
                lastMatchId = matchId,
                lastSeenAt = System.currentTimeMillis()
            )
            data.copy(friends = listOf(friend) + others)
        }
    }

    suspend fun recordCompletedMatch(
        record: SavedMatchRecord,
        participantNames: List<String>
    ) {
        update { data ->
            val stats = data.globalStats.toMutableMap()
            participantNames.forEach { name ->
                val existing = stats[name] ?: PlayerGlobalStats(name = name)
                val points = record.finalScores[name] ?: 0
                val won = name == record.winnerName
                val played = existing.matchesPlayed + 1
                val total = existing.totalPoints + points
                stats[name] = existing.copy(
                    matchesPlayed = played,
                    matchesWon = existing.matchesWon + if (won) 1 else 0,
                    totalPoints = total,
                    averagePointsPerMatch = computeAverage(total, played)
                )
            }
            val history = (listOf(record) + data.matchHistory).take(50)
            val group = participantNames.distinct()
            data.copy(
                globalStats = stats,
                matchHistory = history,
                lastMatchGroup = group,
                lastHostName = record.playerNames.firstOrNull()
            )
        }
        participantNames.forEach { name ->
            rememberFriend(
                name = name,
                hostName = record.playerNames.firstOrNull(),
                matchId = record.matchId
            )
        }
    }
}
