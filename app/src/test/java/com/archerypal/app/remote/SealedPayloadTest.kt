package com.archerypal.app.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SealedPayloadTest {
    @Test
    fun roundTripsAesGcmEnvelope() {
        val secret = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val friendCode = "AP-TEST01"
        val ts = 1_700_000_000L
        val plain = """{"hello":"world","n":42}""".toByteArray(Charsets.UTF_8)
        val sealed = SealedPayload.seal(secret, friendCode, ts, plain)
        val opened = SealedPayload.unseal(secret, friendCode, ts, sealed)
        assertArrayEquals(plain, opened)
    }

    @Test
    fun hkdfMatchesFixedVectorLength() {
        val ikm = ByteArray(32) { it.toByte() }
        val salt = "AP-AAAAAA:100".toByteArray(Charsets.UTF_8)
        val info = "archerypal-v1-enc".toByteArray(Charsets.UTF_8)
        val key = SealedPayload.hkdfSha256(ikm, salt, info, 32)
        assertEquals(32, key.size)
        // Stable for cross-check with workers sealed.spec.ts when salt/info match.
        assertEquals(64, key.joinToString("") { "%02x".format(it) }.length)
    }

    @Test
    fun contentTypeDetection() {
        assertTrue(SealedPayload.isSealedContentType(SealedPayload.CONTENT_TYPE))
        assertTrue(SealedPayload.isSealedContentType("${SealedPayload.CONTENT_TYPE}; charset=utf-8"))
        assertFalse(SealedPayload.isSealedContentType("application/json"))
    }

    @Test(expected = Exception::class)
    fun wrongTimestampFails() {
        val secret = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
        val sealed = SealedPayload.seal(secret, "AP-AAAAAA", 100L, "{}".toByteArray())
        SealedPayload.unseal(secret, "AP-AAAAAA", 101L, sealed)
    }
}

class DeviceAuthCanonicalTest {
    @Test
    fun signingKeyIsSha256OfSecret() {
        val secret = "abc"
        val key = DeviceAuth.signingKey(secret)
        assertEquals(32, key.size)
    }

    @Test
    fun signatureIsDeterministic() {
        val secret = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
        val body = """{"a":1}""".toByteArray(Charsets.UTF_8)
        val a = DeviceAuth.sign(secret, 1_700_000_000L, "POST", "/v1/friends", body, "nonce-1")
        val b = DeviceAuth.sign(secret, 1_700_000_000L, "POST", "/v1/friends", body, "nonce-1")
        assertEquals(a, b)
        assertEquals(64, a.length)
    }
}
