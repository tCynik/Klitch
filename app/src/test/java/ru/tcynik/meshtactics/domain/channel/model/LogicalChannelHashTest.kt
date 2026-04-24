package ru.tcynik.meshtactics.domain.channel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalChannelHashTest {

    private val psk = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    @Test
    fun `compute — deterministic for same inputs`() {
        val h1 = LogicalChannelHash.compute("LongFast", psk)
        val h2 = LogicalChannelHash.compute("LongFast", psk)
        assertEquals(h1, h2)
    }

    @Test
    fun `compute — hex string is exactly 16 characters`() {
        val hash = LogicalChannelHash.compute("LongFast", psk)
        assertEquals(16, hash.value.length)
        assertTrue(hash.value.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `compute — different names produce different hashes`() {
        val h1 = LogicalChannelHash.compute("LongFast", psk)
        val h2 = LogicalChannelHash.compute("LongSlow", psk)
        assertNotEquals(h1, h2)
    }

    @Test
    fun `compute — different PSKs produce different hashes`() {
        val h1 = LogicalChannelHash.compute("LongFast", byteArrayOf(0x01, 0x02))
        val h2 = LogicalChannelHash.compute("LongFast", byteArrayOf(0x03, 0x04))
        assertNotEquals(h1, h2)
    }

    @Test
    fun `compute — name comparison is case-insensitive`() {
        val h1 = LogicalChannelHash.compute("LongFast", psk)
        val h2 = LogicalChannelHash.compute("longfast", psk)
        assertEquals(h1, h2)
    }

    @Test
    fun `compute — mixed case normalized to same hash`() {
        val h1 = LogicalChannelHash.compute("LONGFAST", psk)
        val h2 = LogicalChannelHash.compute("LongFast", psk)
        assertEquals(h1, h2)
    }

    @Test
    fun `compute — empty PSK does not throw and produces valid hash`() {
        val hash = LogicalChannelHash.compute("test", byteArrayOf())
        assertEquals(16, hash.value.length)
    }

    @Test
    fun `compute — empty name and empty PSK produces valid hash`() {
        val hash = LogicalChannelHash.compute("", byteArrayOf())
        assertEquals(16, hash.value.length)
    }
}
