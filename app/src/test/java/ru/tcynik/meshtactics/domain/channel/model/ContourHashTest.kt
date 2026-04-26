package ru.tcynik.meshtactics.domain.channel.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContourHashTest {

    private val psk = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    @Test
    fun `compute — deterministic for same inputs`() {
        val h1 = ContourHash.compute("LongFast", psk)
        val h2 = ContourHash.compute("LongFast", psk)
        assertEquals(h1, h2)
    }

    @Test
    fun `compute — hex string is exactly 16 characters`() {
        val hash = ContourHash.compute("LongFast", psk)
        assertEquals(16, hash.value.length)
        assertTrue(hash.value.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `compute — different names produce different hashes`() {
        val h1 = ContourHash.compute("LongFast", psk)
        val h2 = ContourHash.compute("LongSlow", psk)
        assertNotEquals(h1, h2)
    }

    @Test
    fun `compute — different PSKs produce different hashes`() {
        val h1 = ContourHash.compute("LongFast", byteArrayOf(0x01, 0x02))
        val h2 = ContourHash.compute("LongFast", byteArrayOf(0x03, 0x04))
        assertNotEquals(h1, h2)
    }

    @Test
    fun `compute — name comparison is case-insensitive`() {
        val h1 = ContourHash.compute("LongFast", psk)
        val h2 = ContourHash.compute("longfast", psk)
        assertEquals(h1, h2)
    }

    @Test
    fun `compute — mixed case normalized to same hash`() {
        val h1 = ContourHash.compute("LONGFAST", psk)
        val h2 = ContourHash.compute("LongFast", psk)
        assertEquals(h1, h2)
    }

    @Test
    fun `compute — empty PSK does not throw and produces valid hash`() {
        val hash = ContourHash.compute("test", byteArrayOf())
        assertEquals(16, hash.value.length)
    }

    @Test
    fun `compute — empty name and empty PSK produces valid hash`() {
        val hash = ContourHash.compute("", byteArrayOf())
        assertEquals(16, hash.value.length)
    }

    @Test
    fun `compute base64 overload — consistent with ByteArray overload`() {
        val pskBytes = byteArrayOf(0x01)
        val pskBase64 = java.util.Base64.getEncoder().encodeToString(pskBytes)
        val h1 = ContourHash.compute("LongFast", pskBytes)
        val h2 = ContourHash.compute("LongFast", pskBase64)
        assertEquals(h1, h2)
    }
}
