package com.archerypal.app.remote

import com.archerypal.app.BuildConfig
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object DeviceAuth {
    fun signingKey(deviceSecret: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(deviceSecret.toByteArray(Charsets.UTF_8))

    fun sign(
        deviceSecret: String,
        timestampSeconds: Long,
        method: String,
        path: String,
        body: ByteArray,
        nonce: String = ""
    ): String {
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(body)
        val bodyHashHex = bodyHash.toHex()
        val canonical = "$timestampSeconds$nonce$method$path$bodyHashHex"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(signingKey(deviceSecret), "HmacSHA256"))
        return mac.doFinal(canonical.toByteArray(Charsets.UTF_8)).toHex()
    }

    fun nonceForMethod(method: String): String =
        if (method == "POST" || method == "PUT" || method == "PATCH" || method == "DELETE") {
            UUID.randomUUID().toString()
        } else {
            ""
        }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}

object MatchServerConfig {
    val baseUrl: String
        get() = BuildConfig.MATCH_SERVER_URL.trimEnd('/')

    fun roomWebSocketUrl(roomId: String): String {
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://${baseUrl.removePrefix("https://")}"
            baseUrl.startsWith("http://") -> "ws://${baseUrl.removePrefix("http://")}"
            else -> baseUrl
        }
        return "$wsBase/v1/room/$roomId"
    }
}
