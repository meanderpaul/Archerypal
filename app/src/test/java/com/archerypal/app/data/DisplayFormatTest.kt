package com.archerypal.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class DisplayFormatTest {
    private val eastern = TimeZone.getTimeZone("America/New_York")
    private val locale = Locale.US
    /** Fixed "today" inside the 2026 season for year-inclusion checks. */
    private val nowSep2026 = 1_789_214_400_000L // 2026-09-12T12:00:00Z

    @Test
    fun distanceShowsMiles() {
        assertEquals("0.6 mi away", formatDistanceAway(1.0))
        assertEquals("9.9 mi away", formatDistanceAway(16.0))
        assertEquals("50 mi away", formatDistanceAway(80.4672))
    }

    @Test
    fun sameDayShowsLocalTimeRange() {
        // 2026-04-12 11:00–18:00 UTC → 7:00 AM – 2:00 PM EDT
        val start = 1_775_991_600_000L
        val end = 1_776_016_800_000L
        val text = formatShootWhenRange(start, end, nowMs = nowSep2026, timeZone = eastern, locale = locale)
        assertEquals("Sun, Apr 12 · 7:00 AM – 2:00 PM", text)
    }

    @Test
    fun multiDayWeekendUsesLocalCalendarDates() {
        // Noon UTC May 22–24 2026 stays Fri–Sun in Eastern (unlike midnight UTC → Thu)
        val start = 1_779_451_200_000L
        val end = 1_779_667_199_000L
        val text = formatShootWhenRange(start, end, nowMs = nowSep2026, timeZone = eastern, locale = locale)
        assertEquals("Fri, May 22 – Sun, May 24", text)
    }

    @Test
    fun seasonRangeIncludesYearWhenOutsideCurrentYear() {
        val start = 1_767_268_800_000L // 2026-01-01T12:00:00Z
        val end = 1_790_812_799_000L // 2026-09-30T23:59:59Z
        val now2027 = 1_800_014_400_000L // 2027-01-15T12:00:00Z
        val text = formatShootWhenRange(start, end, nowMs = now2027, timeZone = eastern, locale = locale)
        assertEquals("Jan 1 – Sep 30, 2026", text)
    }

    @Test
    fun seasonRangeInCurrentYearOmitsYear() {
        val start = 1_767_268_800_000L
        val end = 1_790_812_799_000L
        val text = formatShootWhenRange(start, end, nowMs = nowSep2026, timeZone = eastern, locale = locale)
        assertEquals("Thu, Jan 1 – Wed, Sep 30", text)
    }

    @Test
    fun midnightUtcSeasonStartShowsPreviousEveningLocally() {
        // Date.UTC(2026,0,1) lands on Dec 31 evening in Eastern — formatter shows local truth.
        val midnightUtc = 1_767_225_600_000L
        val end = 1_790_812_799_000L
        val text = formatShootWhenRange(midnightUtc, end, nowMs = nowSep2026, timeZone = eastern, locale = locale)
        assertEquals("Dec 31, 2025 – Sep 30, 2026", text)
    }

    @Test
    fun formatShootDayIncludesYearWhenNeeded() {
        val start = 1_820_746_800_000L // 2027-09-12 11:00 UTC
        assertEquals(
            "Sun, Sep 12, 2027",
            formatShootDay(start, nowMs = nowSep2026, timeZone = eastern, locale = locale)
        )
    }

    @Test
    fun parseLocalDateUsesProvidedZone() {
        val ms = parseLocalDateToMillis("2026-01-01", eastern)
        requireNotNull(ms)
        // Eastern midnight Jan 1 2026 = 05:00 UTC
        assertEquals(1_767_243_600_000L, ms)
        assertNull(parseLocalDateToMillis("not-a-date", eastern))
    }
}
