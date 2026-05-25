package ru.tcynik.meshtactics.presentation.feature.marks

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class GeoMarkCreatedAtFormatterTest {

    private val zone = ZoneId.of("Europe/Moscow")

    @Test
    fun `today formats as hour and minute`() {
        val now = 1_748_163_600L // 2025-05-25 12:00:00 MSK
        val created = now - 3_600 // 11:00
        assertEquals("11:00", GeoMarkCreatedAtFormatter.format(created, now, zone))
    }

    @Test
    fun `yesterday formats as month and day`() {
        val now = 1_748_163_600L // 2025-05-25 12:00:00 MSK
        val created = 1_748_077_200L // 2025-05-24 12:00:00 MSK
        assertEquals("5мес.24", GeoMarkCreatedAtFormatter.format(created, now, zone))
    }

    @Test
    fun `older than yesterday uses same date format`() {
        val now = 1_748_163_600L // 2025-05-25 12:00:00 MSK
        val created = 1_747_990_800L // 2025-05-23 12:00:00 MSK
        assertEquals("5мес.23", GeoMarkCreatedAtFormatter.format(created, now, zone))
    }
}
