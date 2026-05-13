package ru.tcynik.meshtactics.domain.channel.usecase

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import java.util.Base64

class SyncContoursOnConnectUseCaseTest {

    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val resolveSlot: ResolveChannelSlotUseCase = mockk()

    private val useCase = SyncContoursOnConnectUseCase(
        observeContours = observeContours,
        observeNodeChannels = observeNodeChannels,
        writeChannel = writeChannel,
        resolveSlot = resolveSlot,
    )

    private val psk = byteArrayOf(0x01, 0x02)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)

    private val emergencyPsk = Base64.getDecoder().decode(DefaultContour.OPEN_PSK)
    private val emergencySlot = NodeChannelSlot(
        index = 0,
        name = DefaultContour.CHANNEL_NAME,
        psk = emergencyPsk,
        isEnabled = true,
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        unmockkStatic(android.util.Log::class)
    }

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

    // ── slot 0 Emergency ──────────────────────────────────────────────────────

    @Test
    fun `writes Emergency to slot 0 when node channels empty`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())

        useCase()

        verify(exactly = 1) { writeChannel.invoke(0, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK) }
    }

    @Test
    fun `skips Emergency write when slot 0 already matches`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))

        useCase()

        verify(exactly = 0) { writeChannel.invoke(0, any(), any()) }
    }

    @Test
    fun `writes Emergency to slot 0 when slot 0 has different channel`() = runTest {
        val wrongSlot0 = emergencySlot.copy(name = "SomethingElse")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongSlot0))

        useCase()

        verify(exactly = 1) { writeChannel.invoke(0, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK) }
    }

    // ── active non-emergency contours ─────────────────────────────────────────

    @Test
    fun `active non-emergency contour on FreeSlot — writeChannel called with correct slot and psk`() = runTest {
        val contour = makeContour("99", "Bravo")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { resolveSlot.invoke(contour, any(), any(), any()) } returns SlotResolution.FreeSlot(2)

        useCase()

        verify(exactly = 1) { writeChannel.invoke(2, "Bravo", pskBase64) }
    }

    @Test
    fun `active non-emergency contour AlreadySynced — no extra writeChannel call`() = runTest {
        val contour = makeContour("99", "Bravo")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { resolveSlot.invoke(contour, any(), any(), any()) } returns SlotResolution.AlreadySynced(3)

        useCase()

        // Only the slot 0 Emergency write (node channels empty → not synced)
        verify(exactly = 1) { writeChannel.invoke(any(), any(), any()) }
    }

    @Test
    fun `inactive contour — writeChannel not called for it`() = runTest {
        val contour = makeContour("99", "Inactive", isActive = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))

        useCase()

        verify(exactly = 1) { writeChannel.invoke(any(), any(), any()) }
        verify(exactly = 1) { writeChannel.invoke(0, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK) }
    }

    // ── no free slots ─────────────────────────────────────────────────────────

    @Test
    fun `no free slots — logs warning, does not crash`() = runTest {
        val contour = makeContour("99", "Bravo")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { resolveSlot.invoke(contour, any(), any(), any()) } returns SlotResolution.NoFreeSlot

        useCase()

        verify { android.util.Log.w(any(), any<String>()) }
    }
}
