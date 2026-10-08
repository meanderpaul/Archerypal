package com.archerypal.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteInviteLinkTest {
    @Test
    fun buildsHttpsLinkAndParsesRoundTrip() {
        val link = RemoteInviteLink.build(
            roomId = "abcd1234",
            joinToken = "tok-xyz",
            hostName = "Paul"
        )
        assertTrue(link.startsWith("https://${LinkConfig.PUBLIC_HOST}/r/"))
        assertFalse(link.contains("Paul"))
        val parsed = RemoteInviteLink.parse(link)
        assertNotNull(parsed)
        assertEquals("abcd1234", parsed!!.first)
        assertEquals("tok-xyz", parsed.second)
    }

    @Test
    fun parsesLegacyCustomSchemeLink() {
        val parsed = RemoteInviteLink.parse("archerypal://remote?roomId=room1&token=join1")
        assertNotNull(parsed)
        assertEquals("room1", parsed!!.first)
        assertEquals("join1", parsed.second)
    }

    @Test
    fun extractsFromSharedText() {
        val text = "Join me: archerypal://remote?roomId=room1&token=join1 thanks"
        val parsed = RemoteInviteLink.extractFromText(text)
        assertNotNull(parsed)
        assertEquals("room1", parsed!!.first)
        assertEquals("join1", parsed.second)
    }

    @Test
    fun extractsHttpsLinkFromSharedText() {
        val text = "Match on! https://${LinkConfig.PUBLIC_HOST}/r/room9?token=t.k-9 see you"
        val parsed = RemoteInviteLink.extractFromText(text)
        assertNotNull(parsed)
        assertEquals("room9", parsed!!.first)
        assertEquals("t.k-9", parsed.second)
    }

    @Test
    fun rejectsGarbage() {
        assertNull(RemoteInviteLink.parse("https://example.com"))
        assertNull(RemoteInviteLink.parse("https://example.com/r/room?token=x"))
        assertNull(RemoteInviteLink.parse("https://${LinkConfig.PUBLIC_HOST}/r/room"))
        assertNull(RemoteInviteLink.parse("archerypal://friend?code=AP-1"))
    }
}
