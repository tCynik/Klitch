package ru.tcynik.klitch.presentation.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.ContourTransport
import ru.tcynik.klitch.domain.channel.model.DefaultActiveContour
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.model.MeshtasticChannel
import java.util.Base64

class GeoMarkAddresseeDefaultsTest {

    private val psk = byteArrayOf(0x01)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)

    private fun makeContour(id: String, name: String, isActive: Boolean = true): Contour {
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = ContourId(id),
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    @Test
    fun `resolveDefault — disconnected returns storage`() {
        val basic = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        assertEquals(
            GEO_MARK_LOCAL_STORAGE_ID,
            resolveDefaultGeoMarkAddresseeId(listOf(basic), isConnected = false),
        )
    }

    @Test
    fun `resolveDefault — connected prefers Basic when active`() {
        val basic = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        val custom = makeContour("custom-id", "Team")
        assertEquals(
            DefaultActiveContour.ID.value,
            resolveDefaultGeoMarkAddresseeId(listOf(custom, basic), isConnected = true),
        )
    }

    @Test
    fun `resolveDefault — connected skips Emergency, uses first non-emergency`() {
        val emergency = DefaultContour.asContour().copy(isActive = true)
        val custom = makeContour("custom-id", "Team")
        assertEquals(
            "custom-id",
            resolveDefaultGeoMarkAddresseeId(listOf(emergency, custom), isConnected = true),
        )
    }

    @Test
    fun `resolveDefault — connected with only Emergency active falls back to storage`() {
        val emergency = DefaultContour.asContour().copy(isActive = true)
        assertEquals(
            GEO_MARK_LOCAL_STORAGE_ID,
            resolveDefaultGeoMarkAddresseeId(listOf(emergency), isConnected = true),
        )
    }

    @Test
    fun `isPersistedGeoMarkAddresseeChoice — local and empty are not persisted choices`() {
        assertFalse(isPersistedGeoMarkAddresseeChoice(""))
        assertFalse(isPersistedGeoMarkAddresseeChoice(GEO_MARK_LOCAL_STORAGE_ID))
        assertTrue(isPersistedGeoMarkAddresseeChoice(DefaultActiveContour.ID.value))
    }
}
