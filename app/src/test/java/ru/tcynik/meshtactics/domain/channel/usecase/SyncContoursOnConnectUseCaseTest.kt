package ru.tcynik.meshtactics.domain.channel.usecase

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import ru.tcynik.meshtactics.logger.NoOpLogger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import java.util.Base64

class SyncContoursOnConnectUseCaseTest {

    private val contourRepository: ContourRepository = mockk(relaxed = true)
    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val resolveSlot: ResolveChannelSlotUseCase = mockk()
    private val writeOwner: WriteOwnerUseCase = mockk(relaxed = true)
    private val observeAppUser: ObserveAppUserUseCase = mockk()

    private val useCase = SyncContoursOnConnectUseCase(
        contourRepository = contourRepository,
        observeContours = observeContours,
        observeNodeChannels = observeNodeChannels,
        writeChannel = writeChannel,
        resolveSlot = resolveSlot,
        writeOwner = writeOwner,
        observeAppUser = observeAppUser,
        observeDeviceConfig = mockk(relaxed = true),
        logger = NoOpLogger(),
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
        coEvery { contourRepository.getPrimaryContourId() } returns DefaultActiveContour.ID
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = ""))
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

    @Test
    fun `writes primary to slot 0 when node channels empty`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))

        useCase()

        verify(exactly = 1) { writeChannel.invoke(0, DefaultActiveContour.DISPLAY_NAME, pskBase64) }
    }

    @Test
    fun `writes Emergency to slot 1 when primary is not Emergency`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))

        useCase()

        verify(exactly = 1) { writeChannel.invoke(1, DefaultContour.CHANNEL_NAME, DefaultContour.OPEN_PSK) }
    }

    @Test
    fun `skips Emergency slot 1 write when primary is Emergency`() = runTest {
        coEvery { contourRepository.getPrimaryContourId() } returns DefaultContour.ID
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(DefaultContour.asContour()))

        useCase()

        verify(exactly = 0) { writeChannel.invoke(1, any(), any()) }
    }

    @Test
    fun `active non-primary contour on FreeSlot — writeChannel from slot 2`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        val bravo = makeContour("99", "Bravo")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary, bravo))
        every { resolveSlot.invoke(bravo, any(), match { it.containsAll(setOf(0, 1)) }, any()) } returns SlotResolution.FreeSlot(2)

        useCase()

        verify(exactly = 1) { writeChannel.invoke(2, "Bravo", pskBase64) }
    }

    @Test
    fun `inactive contour — not written`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        val inactive = makeContour("99", "Inactive", isActive = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary, inactive))

        useCase()

        verify(exactly = 0) { writeChannel.invoke(2, any(), any()) }
    }
}
