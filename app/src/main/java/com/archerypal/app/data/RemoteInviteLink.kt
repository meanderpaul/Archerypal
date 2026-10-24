package com.archerypal.app.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RemoteInviteLink {
    const val SCHEME = "archerypal"
    const val HOST = "remote"

    private val HTTPS_PREFIX = "https://${LinkConfig.PUBLIC_HOST}${LinkConfig.INVITE_PATH}"
    private val CUSTOM_PREFIX = "$SCHEME://$HOST"
    private val ROOM_CODE = Regex("^[A-HJ-NP-Z2-9]{8}$")

    /** [token] is only present on legacy links; new invites are redeemed via the server. */
    data class Invite(val roomId: String, val token: String? = null)

    fun build(roomId: String): String = HTTPS_PREFIX + encode(roomId)

    fun isRoomCode(value: String): Boolean = ROOM_CODE.matches(value.trim().uppercase())

    fun parse(raw: String): Invite? {
        val trimmed = raw.trim().substringBefore(' ').substringBefore('\n')
        return parseHttps(trimmed) ?: parseCustom(trimmed)
    }

    /** Accepts a pasted message containing a link, a bare link, or just the 8-character room code. */
    fun extractFromText(text: String): Invite? {
        parse(text)?.let { return it }
        for (marker in listOf(HTTPS_PREFIX, CUSTOM_PREFIX)) {
            val start = text.indexOf(marker, ignoreCase = true)
            if (start < 0) continue
            val end = text.indexOfAny(charArrayOf(' ', '\n', '\r', '\t'), start)
            val link = if (end < 0) text.substring(start) else text.substring(start, end)
            parse(link)?.let { return it }
        }
        val bare = text.trim().replace("-", "").replace(" ", "")
        if (isRoomCode(bare)) return Invite(bare.uppercase())
        return null
    }

    private fun parseHttps(link: String): Invite? {
        if (!link.startsWith(HTTPS_PREFIX, ignoreCase = true)) return null
        val rest = link.substring(HTTPS_PREFIX.length)
        val roomId = decode(rest.substringBefore('?').substringBefore('/')).trim()
        val token = queryParams(rest.substringAfter('?', missingDelimiterValue = ""))["token"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (roomId.isBlank()) return null
        return Invite(roomId, token)
    }

    private fun parseCustom(link: String): Invite? {
        val schemeSep = link.indexOf("://")
        if (schemeSep <= 0) return null
        val scheme = link.substring(0, schemeSep)
        if (!scheme.equals(SCHEME, ignoreCase = true)) return null
        val pathAndQuery = link.substring(schemeSep + 3)
        val hostPart = pathAndQuery.substringBefore('?').substringBefore('/')
        if (!hostPart.equals(HOST, ignoreCase = true)) return null
        val params = queryParams(pathAndQuery.substringAfter('?', missingDelimiterValue = ""))
        val roomId = params["roomId"]?.trim().orEmpty()
        val token = params["token"]?.trim()?.takeIf { it.isNotBlank() }
        if (roomId.isBlank()) return null
        return Invite(roomId, token)
    }

    private fun queryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else decode(part.substring(0, eq)) to decode(part.substring(eq + 1))
        }.toMap()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%20"), StandardCharsets.UTF_8)
}
