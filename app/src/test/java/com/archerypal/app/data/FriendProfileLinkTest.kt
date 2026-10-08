package com.archerypal.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FriendProfileLinkTest {
    @Test
    fun buildsHttpsLinkAndParsesRoundTrip() {
        val link = FriendProfileLink.build(" ap-abc234 ")
        assertEquals("https://${LinkConfig.PUBLIC_HOST}/f/AP-ABC234", link)
        val parsed = FriendProfileLink.parse(link)
        assertNotNull(parsed)
        assertEquals("AP-ABC234", parsed!!.first)
        assertNull(parsed.second)
    }

    @Test
    fun parsesLegacyCustomSchemeLinkWithName() {
        val parsed = FriendProfileLink.parse("archerypal://friend?code=ap-xyz789&name=Sam%20R")
        assertNotNull(parsed)
        assertEquals("AP-XYZ789", parsed!!.first)
        assertEquals("Sam R", parsed.second)
    }

    @Test
    fun extractsHttpsLinkFromSharedText() {
        val text = "Add me on Archerypal: https://${LinkConfig.PUBLIC_HOST}/f/AP-QRS456"
        val parsed = FriendProfileLink.extractFromText(text)
        assertNotNull(parsed)
        assertEquals("AP-QRS456", parsed!!.first)
    }

    @Test
    fun rejectsOtherLinks() {
        assertNull(FriendProfileLink.parse("https://example.com/f/AP-ABC234"))
        assertNull(FriendProfileLink.parse("https://${LinkConfig.PUBLIC_HOST}/f/"))
        assertNull(FriendProfileLink.parse("archerypal://remote?roomId=r&token=t"))
        assertNull(FriendProfileLink.parse("AP-ABC234"))
    }
}
