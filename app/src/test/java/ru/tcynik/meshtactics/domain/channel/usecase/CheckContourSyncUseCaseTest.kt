package ru.tcynik.meshtactics.domain.channel.usecase

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourSyncResult
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import java.util.Base64
import java.util.UUID

class CheckContourSyncUseCaseTest {

    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()

    private val useCase = CheckContourSyncUseCase(
        observeContours = observeContours,
        observeNodeChannels = observeNodeChannels,
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

    private val emergencyPsk = Base64.getDecoder().decode(DefaultContour.OPEN_PSK)

    private val emergencySlot = NodeChannelSlot(
        index = 0,
        name = DefaultContour.CHANNEL_NAME,
        psk = emergencyPsk,
        isEnabled = true,
    )

    private fun makeContour(name: String, psk: ByteArray, isActive: Boolean = true): Contour {
        val pskBase64 = Base64.getEncoder().encodeToString(psk)
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = ContourId(UUID.randomUUID().toString()),
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
        )
    }

    private fun makeSlot(
        index: Int,
        name: String,
        psk: ByteArray,
        isEnabled: Boolean = true,
        positionPrecision: Int = 32,
    ) = NodeChannelSlot(index = index, name = name, psk = psk, isEnabled = isEnabled, positionPrecision = positionPrecision)

    // ── InSync ────────────────────────────────────────────────────────────────

    @Test
    fun `InSync — slot 0 совпадает с Emergency, активных контуров нет`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))

        assertEquals(ContourSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — активный контур присутствует на ноде`() = runTest {
        val psk = byteArrayOf(0x01, 0x02)
        val contour = makeContour("Alpha", psk)
        val contourSlot = makeSlot(1, "Alpha", psk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot, contourSlot))

        assertEquals(ContourSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — неактивный контур не проверяется`() = runTest {
        val psk = byteArrayOf(0x05)
        val inactive = makeContour("Delta", psk, isActive = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(inactive))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))

        assertEquals(ContourSyncResult.InSync, useCase())
    }

    // ── NeedsSync ─────────────────────────────────────────────────────────────

    @Test
    fun `InSync — список каналов ноды пуст (данные ещё не пришли)`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(emptyList())

        assertEquals(ContourSyncResult.InSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 0 имеет неверное имя`() = runTest {
        val wrongSlot0 = emergencySlot.copy(name = "WrongName")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongSlot0))

        assertEquals(ContourSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 0 имеет неверный PSK`() = runTest {
        val wrongSlot0 = emergencySlot.copy(psk = byteArrayOf(0xFF.toByte()))
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongSlot0))

        assertEquals(ContourSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — активный контур отсутствует на ноде`() = runTest {
        val psk = byteArrayOf(0x03)
        val contour = makeContour("Bravo", psk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))

        assertEquals(ContourSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — контур есть на ноде но isEnabled=false`() = runTest {
        val psk = byteArrayOf(0x04)
        val contour = makeContour("Charlie", psk)
        val disabledSlot = makeSlot(1, "Charlie", psk, isEnabled = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot, disabledSlot))

        assertEquals(ContourSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — контур есть на ноде но position_precision = 0`() = runTest {
        val psk = byteArrayOf(0x06)
        val contour = makeContour("Echo", psk)
        val noPrecisionSlot = makeSlot(1, "Echo", psk, positionPrecision = 0)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot, noPrecisionSlot))

        assertEquals(ContourSyncResult.NeedsSync, useCase())
    }
}
