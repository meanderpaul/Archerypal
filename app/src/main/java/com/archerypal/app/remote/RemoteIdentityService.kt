package com.archerypal.app.remote

import com.archerypal.app.data.PersistedAppData
import com.archerypal.app.data.PersistenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RemoteIdentityService(
    private val persistence: PersistenceRepository,
    private val api: MatchServerApi = MatchServerApi()
) {
    suspend fun migrateStorageIfNeeded() {
        persistence.migrateIfNeeded()
    }

    /** Ensure opaque deviceId exists for sealed HMAC calls (binds legacy installs once). */
    suspend fun ensureDeviceIdBound(): Result<PersistedAppData> {
        migrateStorageIfNeeded()
        var data = persistence.dataFlow.first()
        if (!data.deviceId.isNullOrBlank()) return Result.success(data)
        val code = data.friendCode ?: return Result.success(data)
        val secret = data.deviceSecret ?: return Result.success(data)
        if (!data.serverRegistered) return Result.success(data)
        return withContext(Dispatchers.IO) {
            api.bindDeviceId(code, secret)
        }.fold(
            onSuccess = { resp ->
                persistence.saveDeviceId(resp.deviceId)
                Result.success(persistence.dataFlow.first())
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun ensureRegistered(displayName: String, inviteCode: String? = null): Result<PersistedAppData> {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalStateException("Enter your name first"))
        }
        return registerOnce(trimmed, inviteCode = inviteCode, retryOnConflict = true)
    }

    suspend fun linkGoogleAccount(idToken: String): Result<PersistedAppData> {
        val data = ensureDeviceIdBound().getOrElse { return Result.failure(it) }
        val creds = data.toCredentials()
            ?: return Result.failure(IllegalStateException("Register first"))
        if (!data.serverRegistered) {
            return Result.failure(IllegalStateException("Register with server first"))
        }
        return withContext(Dispatchers.IO) {
            api.linkGoogleAccount(creds, idToken)
        }.fold(
            onSuccess = { response ->
                Result.success(
                    persistence.updateAndGet {
                        it.copy(
                            googleAccountLinked = response.linked,
                            googleAccountEmail = maskEmail(response.email).takeIf { e -> e.isNotBlank() }
                        )
                    }
                )
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun restoreFromGoogle(idToken: String): Result<PersistedAppData> {
        return withContext(Dispatchers.IO) {
            api.restoreGoogleAccount(idToken)
        }.fold(
            onSuccess = { response -> persistGoogleSession(response) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun signInWithGoogle(
        displayName: String,
        idToken: String,
        inviteCode: String? = null
    ): Result<PersistedAppData> {
        val trimmed = displayName.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalStateException("Enter your name first"))
        }
        return withContext(Dispatchers.IO) {
            api.googleSignIn(idToken, trimmed, inviteCode)
        }.fold(
            onSuccess = { response -> persistGoogleSession(response) },
            onFailure = { error ->
                if (error.message?.contains("404", ignoreCase = true) == true ||
                    error.message?.contains("Not Found", ignoreCase = true) == true
                ) {
                    legacyGoogleSignIn(trimmed, idToken, inviteCode)
                } else {
                    Result.failure(error)
                }
            }
        )
    }

    private suspend fun persistGoogleSession(response: GoogleRestoreResponse): Result<PersistedAppData> {
        persistence.saveCredentials(
            friendCode = response.friendCode,
            deviceSecret = response.deviceSecret,
            serverRegistered = true,
            deviceId = response.deviceId
        )
        if (response.deviceId.isNullOrBlank()) {
            ensureDeviceIdBound()
        }
        return Result.success(
            persistence.updateAndGet {
                it.copy(
                    playerName = response.displayName.ifBlank { it.playerName },
                    googleAccountLinked = true,
                    googleAccountEmail = maskEmail(response.email).takeIf { e -> e.isNotBlank() }
                )
            }
        )
    }

    private suspend fun legacyGoogleSignIn(
        displayName: String,
        idToken: String,
        inviteCode: String?
    ): Result<PersistedAppData> {
        val restored = restoreFromGoogle(idToken)
        if (restored.isSuccess) return restored
        val restoreError = restored.exceptionOrNull()?.message.orEmpty()
        val notFound = restoreError.contains("no account linked", ignoreCase = true) ||
            restoreError.contains("not found", ignoreCase = true) ||
            restoreError.contains("404", ignoreCase = true)
        if (!notFound) return restored
        return ensureRegistered(displayName, inviteCode).fold(
            onSuccess = { linkGoogleAccount(idToken) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun refreshGoogleAccountStatus(): Result<PersistedAppData> {
        val data = ensureDeviceIdBound().getOrElse { return Result.failure(it) }
        val creds = data.toCredentials() ?: return Result.success(data)
        if (!data.serverRegistered) return Result.success(data)
        return withContext(Dispatchers.IO) {
            api.googleAccountStatus(creds)
        }.fold(
            onSuccess = { status ->
                Result.success(
                    persistence.updateAndGet {
                        it.copy(
                            googleAccountLinked = status.linked,
                            googleAccountEmail = if (status.linked) {
                                maskEmail(status.email).takeIf { e -> e.isNotBlank() }
                                    ?: it.googleAccountEmail
                            } else {
                                null
                            }
                        )
                    }
                )
            },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun hasLocalIdentity(): Boolean {
        migrateStorageIfNeeded()
        val data = persistence.dataFlow.first()
        return !data.friendCode.isNullOrBlank() && !data.deviceSecret.isNullOrBlank()
    }

    private suspend fun registerOnce(
        displayName: String,
        inviteCode: String?,
        retryOnConflict: Boolean
    ): Result<PersistedAppData> {
        migrateStorageIfNeeded()
        var data = persistence.dataFlow.first()
        if (data.friendCode.isNullOrBlank() || data.deviceSecret.isNullOrBlank()) {
            data = persistence.updateAndGet {
                it.copy(
                    friendCode = FriendCodeGenerator.newFriendCode(),
                    deviceSecret = FriendCodeGenerator.newDeviceSecret(),
                    serverRegistered = false
                )
            }
        }
        if (data.serverRegistered) {
            ensureDeviceIdBound()
            return Result.success(persistence.dataFlow.first())
        }

        val response = withContext(Dispatchers.IO) {
            api.register(
                RegisterRequest(
                    friendCode = data.friendCode!!,
                    displayName = displayName.trim(),
                    deviceSecret = data.deviceSecret!!,
                    inviteCode = inviteCode?.trim()?.takeIf { it.isNotBlank() }
                )
            )
        }
        return response.fold(
            onSuccess = { reg ->
                if (!reg.deviceId.isNullOrBlank()) {
                    persistence.saveCredentials(
                        friendCode = data.friendCode!!,
                        deviceSecret = data.deviceSecret!!,
                        serverRegistered = true,
                        deviceId = reg.deviceId
                    )
                } else {
                    persistence.saveCredentials(
                        friendCode = data.friendCode!!,
                        deviceSecret = data.deviceSecret!!,
                        serverRegistered = true
                    )
                    ensureDeviceIdBound()
                }
                Result.success(
                    persistence.updateAndGet {
                        it.copy(playerName = displayName.trim(), serverRegistered = true)
                    }
                )
            },
            onFailure = { error ->
                val isConflict = retryOnConflict && (
                    error.message?.contains("409", ignoreCase = true) == true ||
                        error.message?.contains("friend code taken", ignoreCase = true) == true
                    )
                if (isConflict) {
                    persistence.updateAndGet {
                        it.copy(
                            friendCode = FriendCodeGenerator.newFriendCode(),
                            serverRegistered = false
                        )
                    }
                    registerOnce(displayName, inviteCode, retryOnConflict = false)
                } else {
                    Result.failure(error)
                }
            }
        )
    }

    suspend fun checkServerReachable(): Boolean = withContext(Dispatchers.IO) {
        api.healthCheck().isSuccess
    }

    /**
     * Clears push token on the server (best-effort), then wipes local credentials.
     */
    suspend fun clearSession() {
        val data = ensureDeviceIdBound().getOrNull() ?: persistence.dataFlow.first()
        val creds = data.toCredentials()
        val name = data.playerName.trim().ifBlank { "Archer" }
        if (creds != null && data.serverRegistered) {
            withContext(Dispatchers.IO) {
                api.updatePlayerMe(
                    creds = creds,
                    displayName = name,
                    fcmToken = null
                )
            }
        }
        com.archerypal.app.push.PushTokenStore.clear()
        persistence.clearCredentials()
    }

    private suspend fun PersistenceRepository.updateAndGet(
        transform: (PersistedAppData) -> PersistedAppData
    ): PersistedAppData {
        update(transform)
        return dataFlow.first()
    }
}

private fun PersistedAppData.toCredentials(): DeviceCredentials? {
    val code = friendCode?.takeIf { it.isNotBlank() } ?: return null
    val secret = deviceSecret?.takeIf { it.isNotBlank() } ?: return null
    val id = deviceId?.takeIf { it.isNotBlank() } ?: return null
    return DeviceCredentials(friendCode = code, deviceId = id, deviceSecret = secret)
}
