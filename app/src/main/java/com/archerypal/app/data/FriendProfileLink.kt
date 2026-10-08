package com.archerypal.app.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FriendProfileLink {
    const val SCHEME = "archerypal"
    const val HOST = "friend"

    private val HTTPS_PREFIX = "https://${LinkConfig.PUBLIC_HOST}${LinkConfig.FRIEND_PATH}"
    private val CUSTOM_PREFIX = "$SCHEME://$HOST"

    /** Display names stay out of shareable URLs; the recipient sees the name after adding. */
    fun build(friendCode: String): String =
        HTTPS_PREFIX + encode(friendCode.trim().uppercase())

    /** Returns the friend code plus an optional display name (legacy custom-scheme links only). */
    fun parse(raw: String): Pair<String, String?>? {
        val trimmed = raw.trim().substringBefore(' ').substringBefore('\n')
        return parseHttps(trimmed) ?: parseCustom(trimmed)
    }

    fun extractFromText(text: String): Pair<String, String?>? {
        parse(text)?.let { return it }
        for (marker in listOf(HTTPS_PREFIX, CUSTOM_PREFIX)) {
            val start = text.indexOf(marker, ignoreCase = true)
            if (start < 0) continue
            val end = text.indexOfAny(charArrayOf(' ', '\n', '\r', '\t'), start)
            val link = if (end < 0) text.substring(start) else text.substring(start, end)
            parse(link)?.let { return it }
        }
        return null
    }

    private fun parseHttps(link: String): Pair<String, String?>? {
        if (!link.startsWith(HTTPS_PREFIX, ignoreCase = true)) return null
        val code = decode(link.substring(HTTPS_PREFIX.length).substringBefore('?').substringBefore('/'))
            .trim()
            .uppercase()
        if (code.isBlank()) return null
        return code to null
    }

    private fun parseCustom(link: String): Pair<String, String?>? {
        val schemeSep = link.indexOf("://")
        if (schemeSep <= 0) return null
        if (!link.substring(0, schemeSep).equals(SCHEME, ignoreCase = true)) return null
        val afterScheme = link.substring(schemeSep + 3)
        val host = afterScheme.substringBefore('?').substringBefore('/')
        if (!host.equals(HOST, ignoreCase = true)) return null
        val params = queryParams(afterScheme.substringAfter('?', missingDelimiterValue = ""))
        val code = params["code"]?.trim().orEmpty()
        if (code.isBlank()) return null
        val name = params["name"]?.trim()?.takeIf { it.isNotBlank() }
        return code.uppercase() to name
    }

    private fun queryParams(query: String): Map<String, String> =
        query.split('&').mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) null else decode(part.substring(0, eq)) to decode(part.substring(eq + 1))
        }.toMap()

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%20"), StandardCharsets.UTF_8)
}
