package ru.tcynik.klitch.data.channel

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.NodeChannelSlot
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ChannelSlotResolverImplTest {

    private val useCase: ObserveNodeChannelsUseCase = mockk()

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun slot(index: Int, name: String, psk: ByteArray, enabled: Boolean = true) =
        NodeChannelSlot(index = index, name = name, psk = psk, isEnabled = enabled)

    private fun resolver(vararg slots: NodeChannelSlot): ChannelSlotResolverImpl {
        every { useCase(NoParams) } returns flowOf(slots.toList())
        return ChannelSlotResolverImpl(useCase)
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `enabled slots — slotToHash populated with correct hashes`() = runTest {
        val psk0 = byteArrayOf(0xAA.toByte())
        val psk1 = byteArrayOf(0xBB.toByte())
        val r = resolver(
            slot(0, "LongFast", psk0),
            slot(1, "MediumSlow", psk1),
        )

        val maps = r.mapsFlow.filter { it.slotToHash.isNotEmpty() }.first()

        assertEquals(ContourHash.compute("LongFast", psk0), maps.slotToHash[0])
        assertEquals(ContourHash.compute("MediumSlow", psk1), maps.slotToHash[1])
    }

    @Test
    fun `enabled slots — hashToSlot is the inverse of slotToHash`() = runTest {
        val psk = byteArrayOf(0x42)
        val r = resolver(slot(2, "Channel2", psk))

        val maps = r.mapsFlow.filter { it.slotToHash.isNotEmpty() }.first()

        val hash = maps.slotToHash[2]!!
        assertEquals(2, maps.hashToSlot[hash])
    }

    @Test
    fun `disabled slots — excluded from both maps`() = runTest {
        val pskEnabled = byteArrayOf(0x01)
        val pskDisabled = byteArrayOf(0x02)
        val r = resolver(
            slot(0, "Enabled", pskEnabled, enabled = true),
            slot(1, "Disabled", pskDisabled, enabled = false),
        )

        val maps = r.mapsFlow.filter { it.slotToHash.isNotEmpty() }.first()

        assertEquals(1, maps.slotToHash.size)
        assertNotNull(maps.slotToHash[0])
        assertNull(maps.slotToHash[1])
        assertEquals(1, maps.hashToSlot.size)
    }

    @Test
    fun `empty slot list — maps stay empty`() {
        every { useCase(NoParams) } returns flowOf(emptyList())
        val r = ChannelSlotResolverImpl(useCase)

        assertTrue(r.mapsFlow.value.slotToHash.isEmpty())
        assertTrue(r.mapsFlow.value.hashToSlot.isEmpty())
    }

    @Test
    fun `maps update when node channels change`() = runTest {
        val slotsFlow = MutableStateFlow<List<NodeChannelSlot>>(emptyList())
        every { useCase(NoParams) } returns slotsFlow

        val r = ChannelSlotResolverImpl(useCase)

        val psk = byteArrayOf(0x55)
        slotsFlow.value = listOf(slot(0, "LongFast", psk))

        val maps = r.mapsFlow.filter { it.slotToHash.isNotEmpty() }.first()
        assertEquals(ContourHash.compute("LongFast", psk), maps.slotToHash[0])
    }

    @Test
    fun `properties slotToHash and hashToSlot reflect current mapsFlow value`() = runTest {
        val psk = byteArrayOf(0x01)
        val r = resolver(slot(0, "LongFast", psk))

        r.mapsFlow.filter { it.slotToHash.isNotEmpty() }.first()

        val expectedHash = ContourHash.compute("LongFast", psk)
        assertEquals(expectedHash, r.slotToHash[0])
        assertEquals(0, r.hashToSlot[expectedHash])
    }
}
