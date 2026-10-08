package com.archerypal.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.archerypal.app.remote.maskEmail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "archerypal")

class PersistenceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val secureStore = SecureCredentialStore(appContext)
    private val dataKey = stringPreferencesKey("app_data_json")

    val dataFlow: Flow<PersistedAppData> = appContext.appDataStore.data.map { prefs ->
        val raw = prefs[dataKey]
        val base = if (raw.isNullOrBlank()) {
            PersistedAppData()
        } else {
            runCatching {
                AppJson.decodeFromString(PersistedAppData.serializer(), raw)
            }.getOrDefault(PersistedAppData())
        }
        val maskedFromSecure = secureStore.getGoogleEmailMasked()
        val maskedEmail = maskedFromSecure
            ?: legacyEmailFromRaw(raw)?.let { maskEmail(it) }
        base.withSecureFields(
            friendCode = secureStore.getFriendCode(),
            deviceSecret = secureStore.getDeviceSecret(),
            deviceId = secureStore.getDeviceId(),
            googleAccountEmail = maskedEmail
        )
    }

    suspend fun migrateIfNeeded() {
        appContext.appDataStore.edit { store ->
            val raw = store[dataKey] ?: return@edit
            if (raw.isBlank()) return@edit

            val element = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() ?: return@edit
            val obj = element.jsonObject
            val legacyCode = obj["friendCode"]?.let { (it as? JsonPrimitive)?.content }?.takeIf { it.isNotBlank() }
            val legacySecret = obj["deviceSecret"]?.let { (it as? JsonPrimitive)?.content }?.takeIf { it.isNotBlank() }
            val legacyEmail = obj["googleAccountEmail"]?.let { (it as? JsonPrimitive)?.content }
                ?.takeIf { it.isNotBlank() }

            if (legacyCode != null && legacySecret != null && !secureStore.hasCredentials()) {
                if (!secureStore.isAvailable) {
                    // Fail closed: do not keep secrets in DataStore JSON either.
                } else {
                    secureStore.saveCredentials(legacyCode, legacySecret)
                }
            }

            if (!legacyEmail.isNullOrBlank() && secureStore.getGoogleEmailMasked().isNullOrBlank()) {
                secureStore.saveGoogleEmailMasked(maskEmail(legacyEmail))
            }

            val stripKeys = setOf("friendCode", "deviceSecret", "googleAccountEmail")
            if (legacyCode != null || legacySecret != null || legacyEmail != null) {
                val cleaned = buildJsonObject {
                    obj.forEach { (key, value) ->
                        if (key !in stripKeys) {
                            put(key, value)
                        }
                    }
                }
                store[dataKey] = cleaned.toString()
            }
        }
    }

    suspend fun update(transform: (PersistedAppData) -> PersistedAppData) {
        appContext.appDataStore.edit { prefs ->
            val current = loadMergedData(prefs[dataKey])
            val updated = transform(current)
            updated.friendCode?.let { code ->
                updated.deviceSecret?.let { secret ->
                    secureStore.saveCredentials(code, secret, updated.deviceId)
                }
            }
            updated.deviceId?.takeIf { it.isNotBlank() }?.let { secureStore.saveDeviceId(it) }
            if (updated.googleAccountLinked) {
                secureStore.saveGoogleEmailMasked(maskEmail(updated.googleAccountEmail))
            } else {
                secureStore.saveGoogleEmailMasked(null)
            }
            prefs[dataKey] = AppJson.encodeToString(
                PersistedAppData.serializer(),
                updated.withSecureFields(
                    friendCode = null,
                    deviceSecret = null,
                    deviceId = null,
                    googleAccountEmail = null
                )
            )
        }
    }

    suspend fun saveCredentials(
        friendCode: String,
        deviceSecret: String,
        serverRegistered: Boolean,
        deviceId: String? = null
    ) {
        if (!secureStore.saveCredentials(friendCode, deviceSecret, deviceId)) {
            update {
                it.copy(serverRegistered = false)
            }
            return
        }
        update {
            it.copy(serverRegistered = serverRegistered)
        }
    }

    suspend fun saveDeviceId(deviceId: String) {
        secureStore.saveDeviceId(deviceId)
        update { it }
    }

    suspend fun clearCredentials() {
        secureStore.clearCredentials()
        update {
            it.copy(serverRegistered = false, googleAccountLinked = false, googleAccountEmail = null)
        }
    }

    suspend fun savePlayerName(name: String) {
        update { it.copy(playerName = name.trim()) }
    }

    suspend fun setAdFree(adFree: Boolean) {
        update { it.copy(isAdFree = adFree) }
    }

    suspend fun setPlusActive(active: Boolean) {
        update { it.copy(isPlusActive = active) }
    }

    suspend fun setGoogleAccountStatus(linked: Boolean, email: String?) {
        val masked = maskEmail(email).takeIf { it.isNotBlank() }
        if (linked) {
            secureStore.saveGoogleEmailMasked(masked)
        } else {
            secureStore.saveGoogleEmailMasked(null)
        }
        update {
            it.copy(
                googleAccountLinked = linked,
                googleAccountEmail = if (linked) masked else null
            )
        }
    }

    suspend fun rememberFriend(
        name: String,
        hostName: String?,
        matchId: String?,
        friendCode: String? = null
    ) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        update { data ->
            val existing = data.friends.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            val others = data.friends.filterNot { it.name.equals(trimmed, ignoreCase = true) }
            val code = friendCode?.trim()?.takeIf { it.isNotBlank() }
                ?: existing?.friendCode?.takeIf { it.isNotBlank() }
                ?: ""
            val friend = SavedFriend(
                friendCode = code,
                name = trimmed,
                lastHostName = hostName ?: existing?.lastHostName,
                lastMatchId = matchId ?: existing?.lastMatchId,
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

    suspend fun updateFeedLastSeen(filter: String, timestamp: Long) {
        update { data ->
            data.copy(feedLastSeenAt = data.feedLastSeenAt + (filter to timestamp))
        }
    }

    private fun loadMergedData(raw: String?): PersistedAppData {
        val base = if (raw.isNullOrBlank()) {
            PersistedAppData()
        } else {
            runCatching {
                AppJson.decodeFromString(PersistedAppData.serializer(), raw)
            }.getOrDefault(PersistedAppData())
        }
        return         base.withSecureFields(
            friendCode = secureStore.getFriendCode(),
            deviceSecret = secureStore.getDeviceSecret(),
            deviceId = secureStore.getDeviceId(),
            googleAccountEmail = secureStore.getGoogleEmailMasked()
        )
    }
}

private fun PersistedAppData.withSecureFields(
    friendCode: String?,
    deviceSecret: String?,
    deviceId: String?,
    googleAccountEmail: String?
): PersistedAppData = copy(
    friendCode = friendCode,
    deviceSecret = deviceSecret,
    deviceId = deviceId,
    googleAccountEmail = googleAccountEmail
)

private fun legacyEmailFromRaw(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val element = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() ?: return null
    val obj = element.jsonObject
    return obj["googleAccountEmail"]?.let { (it as? JsonPrimitive)?.content }?.takeIf { it.isNotBlank() }
}

