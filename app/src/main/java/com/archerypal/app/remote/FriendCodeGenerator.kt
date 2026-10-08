package com.archerypal.app.remote

import java.security.SecureRandom

object FriendCodeGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newFriendCode(): String {
        val random = SecureRandom()
        val suffix = buildString(6) {
            repeat(6) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
        return "AP-$suffix"
    }

    fun newDeviceSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
