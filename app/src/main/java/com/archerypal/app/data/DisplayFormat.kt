package com.archerypal.app.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

fun formatRelativeTime(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = (nowMs - epochMs).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        minutes < 60 * 24 * 7 -> "${minutes / (60 * 24)}d ago"
        else -> formatInLocalZone("MMM d", epochMs)
    }
}

private const val KM_PER_MILE = 1.609344

/** Server distances are kilometers; the app shows miles (`0.4 mi away`, `12 mi away`). */
fun formatDistanceAway(distanceKm: Double): String {
    val miles = distanceKm / KM_PER_MILE
    return if (miles < 10) {
        String.format(Locale.US, "%.1f mi away", miles)
    } else {
        String.format(Locale.US, "%d mi away", Math.round(miles))
    }
}

fun formatMatchDate(epochMs: Long): String =
    formatInLocalZone("EEE, MMM d", epochMs)

/**
 * Formats a shoot start/end window in the device's local timezone.
 *
 * - Same local day: `Sun, Apr 12 · 7:00 AM – 2:00 PM`
 * - Multi-day (weekend / season): `Fri, May 22 – Sun, May 24` or `Jan 1 – Sep 30, 2026`
 * - Year is included when the range is outside the current local year
 */
fun formatShootWhenRange(
    startsAtMs: Long,
    endsAtMs: Long?,
    nowMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val endMs = endsAtMs?.takeIf { it > startsAtMs }
    val currentYear = yearInZone(nowMs, timeZone)
    val startYear = yearInZone(startsAtMs, timeZone)
    val endYear = endMs?.let { yearInZone(it, timeZone) } ?: startYear
    val includeYear = startYear != currentYear || endYear != currentYear || startYear != endYear

    if (endMs == null) {
        val pattern = if (includeYear) "EEE, MMM d, yyyy · h:mm a" else "EEE, MMM d · h:mm a"
        return formatInZone(pattern, startsAtMs, timeZone, locale)
    }

    if (localDayKey(startsAtMs, timeZone) == localDayKey(endMs, timeZone)) {
        val dayPattern = if (includeYear) "EEE, MMM d, yyyy" else "EEE, MMM d"
        val day = formatInZone(dayPattern, startsAtMs, timeZone, locale)
        val startTime = formatInZone("h:mm a", startsAtMs, timeZone, locale)
        val endTime = formatInZone("h:mm a", endMs, timeZone, locale)
        return if (startTime == endTime) {
            "$day · $startTime"
        } else {
            "$day · $startTime – $endTime"
        }
    }

    // Multi-day / season: calendar dates only (avoids midnight-UTC looking like the prior evening).
    return when {
        startYear != endYear -> {
            val start = formatInZone("MMM d, yyyy", startsAtMs, timeZone, locale)
            val end = formatInZone("MMM d, yyyy", endMs, timeZone, locale)
            "$start – $end"
        }
        includeYear -> {
            val start = formatInZone("MMM d", startsAtMs, timeZone, locale)
            val end = formatInZone("MMM d, yyyy", endMs, timeZone, locale)
            "$start – $end"
        }
        else -> {
            val start = formatInZone("EEE, MMM d", startsAtMs, timeZone, locale)
            val end = formatInZone("EEE, MMM d", endMs, timeZone, locale)
            "$start – $end"
        }
    }
}

/** Compact local day label for lists (includes year when not the current year). */
fun formatShootDay(
    startsAtMs: Long,
    nowMs: Long = System.currentTimeMillis(),
    timeZone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault()
): String {
    val includeYear = yearInZone(startsAtMs, timeZone) != yearInZone(nowMs, timeZone)
    val pattern = if (includeYear) "EEE, MMM d, yyyy" else "EEE, MMM d"
    return formatInZone(pattern, startsAtMs, timeZone, locale)
}

/** Parses `yyyy-MM-dd` as local midnight on the device. */
fun parseLocalDateToMillis(
    raw: String,
    timeZone: TimeZone = TimeZone.getDefault()
): Long? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            this.timeZone = timeZone
            isLenient = false
        }.parse(trimmed)?.time
    }.getOrNull()
}

private fun formatInLocalZone(pattern: String, epochMs: Long): String =
    formatInZone(pattern, epochMs, TimeZone.getDefault(), Locale.getDefault())

private fun formatInZone(
    pattern: String,
    epochMs: Long,
    timeZone: TimeZone,
    locale: Locale
): String =
    SimpleDateFormat(pattern, locale).apply {
        this.timeZone = timeZone
    }.format(Date(epochMs))

private fun yearInZone(epochMs: Long, timeZone: TimeZone): Int {
    val cal = Calendar.getInstance(timeZone)
    cal.timeInMillis = epochMs
    return cal.get(Calendar.YEAR)
}

private fun localDayKey(epochMs: Long, timeZone: TimeZone): String =
    formatInZone("yyyy-MM-dd", epochMs, timeZone, Locale.US)
