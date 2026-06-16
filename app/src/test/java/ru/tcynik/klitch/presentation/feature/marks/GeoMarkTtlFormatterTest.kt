package ru.tcynik.klitch.presentation.feature.marks

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.ui.UiText

class GeoMarkTtlFormatterTest {

    private val now = 1_000_000L

    @Test
    fun `null expiresAt — dash`() {
        assertEquals(UiText.Raw("—"), GeoMarkTtlFormatter.format(null, now))
    }

    @Test
    fun `expired — ttl_expired resource`() {
        assertEquals(UiText.Static(R.string.geo_mark_ttl_expired), GeoMarkTtlFormatter.format(now - 1, now))
        assertEquals(UiText.Static(R.string.geo_mark_ttl_expired), GeoMarkTtlFormatter.format(now, now))
    }

    @Test
    fun `under one minute — less_minute resource`() {
        assertEquals(UiText.Static(R.string.geo_mark_ttl_less_minute), GeoMarkTtlFormatter.format(now + 30, now))
        assertEquals(UiText.Static(R.string.geo_mark_ttl_less_minute), GeoMarkTtlFormatter.format(now + 59, now))
    }

    @Test
    fun `minutes only — rounded up`() {
        assertEquals(UiText.Dynamic(R.string.geo_mark_ttl_minutes, listOf(1)), GeoMarkTtlFormatter.format(now + 60, now))
        assertEquals(UiText.Dynamic(R.string.geo_mark_ttl_minutes, listOf(2)), GeoMarkTtlFormatter.format(now + 61, now))
        assertEquals(UiText.Dynamic(R.string.geo_mark_ttl_minutes, listOf(5)), GeoMarkTtlFormatter.format(now + 300, now))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals(UiText.Dynamic(R.string.geo_mark_ttl_hours, listOf(1)), GeoMarkTtlFormatter.format(now + 3_600, now))
        assertEquals(UiText.Dynamic(R.string.geo_mark_ttl_hours_minutes, listOf(1, 30)), GeoMarkTtlFormatter.format(now + 5_400, now))
        assertEquals(UiText.Dynamic(R.string.geo_mark_ttl_hours_minutes, listOf(2, 15)), GeoMarkTtlFormatter.format(now + 8_100, now))
    }
}
