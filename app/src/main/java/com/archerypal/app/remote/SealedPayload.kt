package com.archerypal.app.remote

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM envelope matching workers/src/util/sealed.ts.
 * Key = HKDF-SHA256(ikm=SHA256(deviceSecret), salt=friendCode:timestamp, info=archerypal-v1-enc).
 */
object SealedPayload {
    const val CONTENT_TYPE = "application/vnd.archerypal.sealed+json"
    private const val INFO = "archerypal-v1-enc"

    fun seal(
        deviceSecret: String,
        friendCode: String,
        timestampSeconds: Long,
        plaintext: ByteArray
    ): ByteArray {
        val key = deriveKey(deviceSecret, friendCode, timestampSeconds)
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(plaintext)
        val ct = sealed.copyOfRange(0, sealed.size - 16)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ctB64 = Base64.getEncoder().encodeToString(ct)
        val tagB64 = Base64.getEncoder().encodeToString(tag)
        val json =
            """{"v":1,"iv":"$ivB64","ct":"$ctB64","tag":"$tagB64"}"""
        return json.toByteArray(Charsets.UTF_8)
    }

    fun unseal(
        deviceSecret: String,
        friendCode: String,
        timestampSeconds: Long,
        envelopeBytes: ByteArray
    ): ByteArray {
        val text = String(envelopeBytes, Charsets.UTF_8)
        require(text.contains("\"v\":1") || text.contains("\"v\": 1")) { "unsupported seal version" }
        val iv = Base64.getDecoder().decode(jsonStringField(text, "iv"))
        val ct = Base64.getDecoder().decode(jsonStringField(text, "ct"))
        val tag = Base64.getDecoder().decode(jsonStringField(text, "tag"))
        val combined = ByteArray(ct.size + tag.size)
        System.arraycopy(ct, 0, combined, 0, ct.size)
        System.arraycopy(tag, 0, combined, ct.size, tag.size)
        val key = deriveKey(deviceSecret, friendCode, timestampSeconds)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(combined)
    }

    fun isSealedContentType(value: String?): Boolean =
        value?.contains(CONTENT_TYPE, ignoreCase = true) == true

    private fun jsonStringField(json: String, name: String): String {
        val key = "\"$name\""
        val idx = json.indexOf(key)
        require(idx >= 0) { "missing $name" }
        val colon = json.indexOf(':', idx + key.length)
        val firstQuote = json.indexOf('"', colon + 1)
        val secondQuote = json.indexOf('"', firstQuote + 1)
        require(firstQuote >= 0 && secondQuote > firstQuote) { "invalid $name" }
        return json.substring(firstQuote + 1, secondQuote)
    }

    private fun deriveKey(deviceSecret: String, friendCode: String, timestampSeconds: Long): ByteArray {
        val ikm = DeviceAuth.signingKey(deviceSecret)
        val salt = "$friendCode:$timestampSeconds".toByteArray(Charsets.UTF_8)
        val info = INFO.toByteArray(Charsets.UTF_8)
        return hkdfSha256(ikm, salt, info, 32)
    }

    /** RFC 5869 HKDF-SHA256. */
    internal fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(actualSalt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen
        val okm = ByteArrayOutputStream()
        var prev = ByteArray(0)
        for (i in 1..n) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(prev)
            mac.update(info)
            mac.update(i.toByte())
            prev = mac.doFinal()
            okm.write(prev)
        }
        return okm.toByteArray().copyOf(length)
    }
}
