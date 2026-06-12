package ru.tcynik.meshtactics.domain.channel.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourResolution
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import java.util.Base64

class ResolveContourFromSlotUseCaseTest {

    private val useCase = ResolveContourFromSlotUseCase()

    private val psk = byteArrayOf(0x11, 0x22)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)
    private val primaryId = ContourId("primary-uuid")
    private val primaryHash = ContourHash.compute("Primary", psk)
    private val customHash = ContourHash.compute("LongFast", psk)

    private val primaryContour = contour(primaryId, "Primary", isActive = true, hash = primaryHash)
    private val emergencyContour = DefaultContour.asContour()
    private val customContour = contour(ContourId("custom-uuid"), "LongFast", isActive = true, hash = customHash)
    private val inactiveContour = customContour.copy(isActive = false)

    private val maps = ChannelSlotMaps(
        slotToHash = mapOf(2 to customHash),
        hashToSlot = mapOf(customHash to 2),
    )

    @Test
    fun `slot 0 — Deliver primary contour`() {
        val result = useCase(0, listOf(primaryContour, emergencyContour), maps, primaryId, sosMode = false)
        assertEquals(ContourResolution.Deliver(primaryContour), result)
    }

    @Test
    fun `slot 0 primary not found — Drop`() {
        val result = useCase(0, listOf(emergencyContour), maps, primaryId, sosMode = false)
        assertTrue(result is ContourResolution.Drop)
    }

    @Test
    fun `slot 1 SOS on — Deliver Emergency`() {
        val result = useCase(1, listOf(primaryContour, emergencyContour), maps, primaryId, sosMode = true)
        assertEquals(ContourResolution.Deliver(emergencyContour), result)
    }

    @Test
    fun `slot 1 SOS off — SilentStore Emergency`() {
        val result = useCase(1, listOf(primaryContour, emergencyContour), maps, primaryId, sosMode = false)
        assertEquals(ContourResolution.SilentStore(emergencyContour), result)
    }

    @Test
    fun `slot 1 emergency contour not found — Drop`() {
        val result = useCase(1, listOf(primaryContour), maps, primaryId, sosMode = false)
        assertTrue(result is ContourResolution.Drop)
    }

    @Test
    fun `slot N hash found isActive — Deliver`() {
        val result = useCase(2, listOf(primaryContour, customContour), maps, primaryId, sosMode = false)
        assertEquals(ContourResolution.Deliver(customContour), result)
    }

    @Test
    fun `slot N hash found isActive false — Drop`() {
        val result = useCase(2, listOf(primaryContour, inactiveContour), maps, primaryId, sosMode = false)
        assertTrue(result is ContourResolution.Drop)
    }

    @Test
    fun `slot N hash not in maps — Drop`() {
        val result = useCase(5, listOf(primaryContour, customContour), maps, primaryId, sosMode = false)
        assertTrue(result is ContourResolution.Drop)
    }

    @Test
    fun `slot N hash found no matching contour — Drop`() {
        val orphanHash = ContourHash.compute("Orphan", byteArrayOf(0xFF.toByte()))
        val orphanMaps = ChannelSlotMaps(
            slotToHash = mapOf(3 to orphanHash),
            hashToSlot = mapOf(orphanHash to 3),
        )
        val result = useCase(3, listOf(primaryContour), orphanMaps, primaryId, sosMode = false)
        assertTrue(result is ContourResolution.Drop)
    }

    private fun contour(
        id: ContourId,
        name: String,
        isActive: Boolean,
        hash: ContourHash,
    ) = Contour(
        id = id,
        name = name,
        description = null,
        expiration = null,
        exclusivityTime = null,
        isActive = isActive,
        transport = ContourTransport(MeshtasticChannel(psk = pskBase64, channelHash = hash)),
    )
}
