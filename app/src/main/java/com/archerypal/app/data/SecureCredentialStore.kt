package com.archerypal.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores device identity credentials outside readable DataStore JSON.
 * Fails closed: if encrypted storage is unavailable, credentials are not stored
 * or returned (remote features stay disabled).
 */
class SecureCredentialStore(context: Context) {

    private val appContext = context.applicationContext

    private val encryptedPrefs: SharedPreferences? by lazy { createEncryptedPrefs() }

    /** False when MasterKey / EncryptedSharedPreferences cannot be opened. */
    val isAvailable: Boolean
        get() = encryptedPrefs != null

    private fun createEncryptedPrefs(): SharedPreferences? {
        return try {
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Encrypted prefs unavailable; refusing plaintext credential fallback", t)
            runCatching {
                appContext.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
            }
            null
        }
    }

    fun getFriendCode(): String? =
        encryptedPrefs?.getString(KEY_FRIEND_CODE, null)?.takeIf { it.isNotBlank() }

    fun getDeviceSecret(): String? =
        encryptedPrefs?.getString(KEY_DEVICE_SECRET, null)?.takeIf { it.isNotBlank() }

    fun getDeviceId(): String? =
        encryptedPrefs?.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    fun getGoogleEmailMasked(): String? =
        encryptedPrefs?.getString(KEY_GOOGLE_EMAIL_MASKED, null)?.takeIf { it.isNotBlank() }

    fun saveCredentials(friendCode: String, deviceSecret: String, deviceId: String? = null): Boolean {
        val prefs = encryptedPrefs ?: return false
        prefs.edit().apply {
            putString(KEY_FRIEND_CODE, friendCode)
            putString(KEY_DEVICE_SECRET, deviceSecret)
            val id = deviceId?.trim()?.takeIf { it.isNotBlank() }
            if (id != null) putString(KEY_DEVICE_ID, id)
        }.apply()
        return true
    }

    fun saveDeviceId(deviceId: String): Boolean {
        val prefs = encryptedPrefs ?: return false
        val id = deviceId.trim().takeIf { it.isNotBlank() } ?: return false
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return true
    }

    fun saveGoogleEmailMasked(masked: String?) {
        val prefs = encryptedPrefs ?: return
        val value = masked?.trim()?.takeIf { it.isNotBlank() }
        prefs.edit().apply {
            if (value == null) remove(KEY_GOOGLE_EMAIL_MASKED)
            else putString(KEY_GOOGLE_EMAIL_MASKED, value)
        }.apply()
    }

    fun clearCredentials() {
        encryptedPrefs?.edit()
            ?.remove(KEY_FRIEND_CODE)
            ?.remove(KEY_DEVICE_SECRET)
            ?.remove(KEY_DEVICE_ID)
            ?.remove(KEY_GOOGLE_EMAIL_MASKED)
            ?.apply()
    }

    fun hasCredentials(): Boolean =
        !getFriendCode().isNullOrBlank() && !getDeviceSecret().isNullOrBlank()

    companion object {
        private const val TAG = "SecureCredentialStore"
        private const val PREFS_NAME = "archerypal_secure_credentials"
        private const val KEY_FRIEND_CODE = "friend_code"
        private const val KEY_DEVICE_SECRET = "device_secret"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_GOOGLE_EMAIL_MASKED = "google_email_masked"
    }
}
