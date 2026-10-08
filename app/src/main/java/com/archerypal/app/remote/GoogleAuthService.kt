package com.archerypal.app.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.archerypal.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

data class GoogleSignInResult(val idToken: String, val email: String?)

class GoogleAuthService(private val context: Context) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(): Result<GoogleSignInResult> {
        val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
        if (serverClientId.isBlank()) {
            return Result.failure(
                IllegalStateException("Google Sign-In is not configured (GOOGLE_WEB_CLIENT_ID)")
            )
        }
        return try {
            Result.success(requestCredential(serverClientId, SignInMode.Button))
        } catch (first: GetCredentialException) {
            if (!isNoCredentials(first)) {
                return Result.failure(mapCredentialError(first))
            }
            try {
                Result.success(requestCredential(serverClientId, SignInMode.AllAccounts))
            } catch (second: Exception) {
                Result.failure(mapCredentialError(second))
            }
        } catch (e: Exception) {
            Result.failure(mapCredentialError(e))
        }
    }

    private enum class SignInMode {
        Button,
        AllAccounts
    }

    private suspend fun requestCredential(serverClientId: String, mode: SignInMode): GoogleSignInResult {
        val option = when (mode) {
            SignInMode.Button -> GetSignInWithGoogleOption.Builder(serverClientId).build()
            SignInMode.AllAccounts -> GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(serverClientId)
                .build()
        }
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .setPreferImmediatelyAvailableCredentials(false)
            .build()
        val response = credentialManager.getCredential(context, request)
        return extractIdToken(response)
    }

    private fun extractIdToken(response: GetCredentialResponse): GoogleSignInResult {
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            // For Google ID credentials, `id` is the account email address.
            val email = googleCred.id.trim().takeIf { it.contains('@') }
            return GoogleSignInResult(googleCred.idToken, email)
        }
        throw IllegalStateException("Unexpected credential type")
    }

    private fun isNoCredentials(e: GetCredentialException): Boolean =
        e is NoCredentialException ||
            e.message?.contains("No credentials", ignoreCase = true) == true

    private fun mapCredentialError(e: Exception): Exception {
        val msg = buildString {
            append(e.message.orEmpty())
            e.cause?.message?.let { append(' ').append(it) }
        }
        if (e is NoCredentialException ||
            msg.contains("No credentials", ignoreCase = true)
        ) {
            return IllegalStateException(
                "No Google account available on this device. " +
                    "Sign into Google in Settings, and add an Android OAuth client for package " +
                    "${context.packageName} with SHA-1 from ./gradlew :app:signingReport " +
                    "(see docs/GOOGLE_SIGNIN_SETUP.md).",
                e
            )
        }
        if (msg.contains("reauth failed", ignoreCase = true) ||
            msg.contains("[16]", ignoreCase = false)
        ) {
            return IllegalStateException(
                "Google could not verify this app (error 16: reauth failed). " +
                    "This almost always means the Android OAuth client in Google Cloud " +
                    "does not match this install. For Run-from-Android-Studio builds use " +
                    "package ${context.packageName} and SHA-1 from ./gradlew :app:signingReport " +
                    "(debug variant). The Web client ID goes in build.gradle.kts only — " +
                    "do not use the Android client ID there. " +
                    "OAuth consent screen User type must be External. " +
                    "After fixing Cloud Console, uninstall the app, wait a few minutes, reinstall.",
                e
            )
        }
        if (msg.contains("test user", ignoreCase = true) ||
            msg.contains("not a tester", ignoreCase = true) ||
            msg.contains("access_denied", ignoreCase = true) ||
            msg.contains("403", ignoreCase = true) ||
            msg.contains("not authorized", ignoreCase = true)
        ) {
            return IllegalStateException(
                "Google blocked sign-in: this account is not on the OAuth test-user list. " +
                    "In Google Cloud Console (same project as your Web client ID), open " +
                    "APIs & Services → OAuth consent screen → Test users, and add the exact " +
                    "Gmail address you pick on your phone. This is not the same as Play Console testers. " +
                    "Consent screen User type must be External for personal Gmail. " +
                    "Changes can take a few minutes.",
                e
            )
        }
        return e
    }
}

fun maskEmail(email: String?): String {
    if (email.isNullOrBlank()) return ""
    val at = email.indexOf('@')
    if (at <= 1) return "***"
    val local = email.substring(0, at)
    val domain = email.substring(at)
    // Already masked (e.g. from server) — keep as-is.
    if (local.endsWith("***") || local.contains("***")) return email
    val visible = local.take(2)
    return "$visible***$domain"
}
