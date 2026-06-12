package ru.tcynik.klitch.presentation.feature.marks

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoMarkTtlFormatterTest {

    private val now = 1_000_000L

    @Test
    fun `null expiresAt — dash`() {
        assertEquals("—", GeoMarkTtlFormatter.format(null, now))
    }

    @Test
    fun `expired — истёк`() {
        assertEquals("истёк", GeoMarkTtlFormatter.format(now - 1, now))
        assertEquals("истёк", GeoMarkTtlFormatter.format(now, now))
    }

    @Test
    fun `under one minute — less than one minute label`() {
        assertEquals("<1 мин.", GeoMarkTtlFormatter.format(now + 30, now))
        assertEquals("<1 мин.", GeoMarkTtlFormatter.format(now + 59, now))
    }

    @Test
    fun `minutes only — rounded up`() {
        assertEquals("1 мин.", GeoMarkTtlFormatter.format(now + 60, now))
        assertEquals("2 мин.", GeoMarkTtlFormatter.format(now + 61, now))
        assertEquals("5 мин.", GeoMarkTtlFormatter.format(now + 300, now))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals("1ч", GeoMarkTtlFormatter.format(now + 3_600, now))
        assertEquals("1ч 30м", GeoMarkTtlFormatter.format(now + 5_400, now))
        assertEquals("2ч 15м", GeoMarkTtlFormatter.format(now + 8_100, now))
    }
}
