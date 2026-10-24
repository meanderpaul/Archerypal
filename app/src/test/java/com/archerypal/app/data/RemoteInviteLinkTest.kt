package com.archerypal.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteInviteLinkTest {
    @Test
    fun buildsShortHttpsLinkAndParsesRoundTrip() {
        val link = RemoteInviteLink.build("K7QM2XRP")
        assertEquals("https://${LinkConfig.PUBLIC_HOST}/r/K7QM2XRP", link)
        val parsed = RemoteInviteLink.parse(link)
        assertEquals(RemoteInviteLink.Invite("K7QM2XRP"), parsed)
    }

    @Test
    fun parsesLegacyLinksWithToken() {
        val custom = RemoteInviteLink.parse("archerypal://remote?roomId=room1&token=join1")
        assertEquals(RemoteInviteLink.Invite("room1", "join1"), custom)
        val https = RemoteInviteLink.parse("https://${LinkConfig.PUBLIC_HOST}/r/room9?token=t.k-9")
        assertEquals(RemoteInviteLink.Invite("room9", "t.k-9"), https)
    }

    @Test
    fun parsesCustomSchemeWithoutToken() {
        val parsed = RemoteInviteLink.parse("archerypal://remote?roomId=K7QM2XRP")
        assertEquals(RemoteInviteLink.Invite("K7QM2XRP"), parsed)
    }

    @Test
    fun extractsLinkFromSharedText() {
        val text = "Paul challenged you! https://${LinkConfig.PUBLIC_HOST}/r/K7QM2XRP see you"
        val parsed = RemoteInviteLink.extractFromText(text)
        assertNotNull(parsed)
        assertEquals("K7QM2XRP", parsed!!.roomId)
        assertNull(parsed.token)
    }

    @Test
    fun acceptsBareRoomCode() {
        assertEquals(RemoteInviteLink.Invite("K7QM2XRP"), RemoteInviteLink.extractFromText(" k7qm2xrp "))
        assertEquals(RemoteInviteLink.Invite("K7QM2XRP"), RemoteInviteLink.extractFromText("K7QM-2XRP"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(RemoteInviteLink.parse("https://example.com"))
        assertNull(RemoteInviteLink.parse("https://example.com/r/room?token=x"))
        assertNull(RemoteInviteLink.parse("archerypal://friend?code=AP-1"))
        assertNull(RemoteInviteLink.extractFromText("hello there"))
        assertNull(RemoteInviteLink.extractFromText("K7QM2XR0"))
    }
}
