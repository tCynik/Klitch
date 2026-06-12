package ru.tcynik.klitch.domain.channel.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import ru.tcynik.klitch.logger.NoOpLogger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourHash
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.model.ContourTransport
import ru.tcynik.klitch.domain.channel.model.DefaultActiveContour
import ru.tcynik.klitch.domain.channel.model.DefaultContour
import ru.tcynik.klitch.domain.channel.model.MeshtasticChannel
import ru.tcynik.klitch.domain.channel.model.NodeChannelSlot
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.klitch.domain.mesh.model.ChannelPositionPrecision
import ru.tcynik.klitch.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisableNodePositionBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.IsPositionSmartBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.PrepareNodeForAppDrivenBroadcastUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetPositionBroadcastSecsUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.klitch.domain.user.model.AppUser
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import java.util.Base64

class SyncContoursOnConnectUseCaseTest {

    private val contourRepository: ContourRepository = mockk(relaxed = true)
    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val beginSettingsEdit: BeginSettingsEditUseCase = mockk(relaxed = true)
    private val commitSettingsEdit: CommitSettingsEditUseCase = mockk(relaxed = true)
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val resolveSlot: ResolveChannelSlotUseCase = mockk()
    private val writeOwner: WriteOwnerUseCase = mockk(relaxed = true)
    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val prepareNodeForAppDrivenBroadcast: PrepareNodeForAppDrivenBroadcastUseCase = mockk(relaxed = true)
    private val disableNodePositionBroadcast: DisableNodePositionBroadcastUseCase = mockk(relaxed = true)
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase = mockk()
    private val observeEmergencyMode: ObserveEmergencyModeUseCase = mockk()
    private val getPositionBroadcastSecs: GetPositionBroadcastSecsUseCase = mockk()

    private val isPositionSmartBroadcastEnabled: IsPositionSmartBroadcastEnabledUseCase = mockk()

    private val useCase = SyncContoursOnConnectUseCase(
        contourRepository = contourRepository,
        observeContours = observeContours,
        observeNodeChannels = observeNodeChannels,
        beginSettingsEdit = beginSettingsEdit,
        commitSettingsEdit = commitSettingsEdit,
        writeChannel = writeChannel,
        resolveSlot = resolveSlot,
        writeOwner = writeOwner,
        observeAppUser = observeAppUser,
        observeDeviceConfig = mockk(relaxed = true),
        prepareNodeForAppDrivenBroadcast = prepareNodeForAppDrivenBroadcast,
        disableNodePositionBroadcast = disableNodePositionBroadcast,
        observeGpsBroadcastEnabled = observeGpsBroadcastEnabled,
        observeEmergencyMode = observeEmergencyMode,
        getPositionBroadcastSecs = getPositionBroadcastSecs,
        isPositionSmartBroadcastEnabled = isPositionSmartBroadcastEnabled,
        logger = NoOpLogger(),
    )

    private val psk = byteArrayOf(0x01, 0x02)
    private val pskBase64 = Base64.getEncoder().encodeToString(psk)

    @Before
    fun setUp() {
        coEvery { contourRepository.getPrimaryContourId() } returns DefaultActiveContour.ID
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = ""))
        coEvery { beginSettingsEdit.invoke() } returns true
        // Default: broadcast enabled, no SOS, node already silenced (MAX_VALUE) → no broadcast write
        every { observeGpsBroadcastEnabled.invoke() } returns flowOf(true)
        every { observeEmergencyMode.invoke() } returns flowOf(false)
        coEvery { getPositionBroadcastSecs.invoke() } returns Int.MAX_VALUE
        coEvery { isPositionSmartBroadcastEnabled.invoke() } returns false
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
    fun `writes primary to slot 0 when node has default LongFast channel`() = runTest {
        val openPskBytes = Base64.getDecoder().decode(DefaultContour.OPEN_PSK)
        val defaultPskBytes = Base64.getDecoder().decode(DefaultActiveContour.DEFAULT_PSK)
        val primary = Contour(
            id = DefaultActiveContour.ID,
            name = DefaultActiveContour.DISPLAY_NAME,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(
                meshtastic = MeshtasticChannel(
                    psk = DefaultActiveContour.DEFAULT_PSK,
                    channelHash = ContourHash.compute(DefaultActiveContour.CHANNEL_NAME, defaultPskBytes),
                ),
            ),
        )
        val defaultSlot = NodeChannelSlot(
            index = 0,
            name = DefaultContour.CHANNEL_NAME,
            psk = openPskBytes,
            isEnabled = true,
        )
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(defaultSlot))

        useCase()

        coVerify(exactly = 1) { beginSettingsEdit.invoke() }
        coVerify(exactly = 1) {
            writeChannel.invoke(0, DefaultActiveContour.CHANNEL_NAME, DefaultActiveContour.DEFAULT_PSK)
        }
        coVerify(exactly = 1) { commitSettingsEdit.invoke() }
    }

    @Test
    fun `writes primary to slot 0 when node channels empty`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))

        useCase()

        coVerify(exactly = 1) { beginSettingsEdit.invoke() }
        coVerify(exactly = 1) { writeChannel.invoke(0, DefaultActiveContour.CHANNEL_NAME, pskBase64) }
        coVerify(exactly = 1) { commitSettingsEdit.invoke() }
    }

    @Test
    fun `writes Emergency to slot 1 when primary is not Emergency`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))

        useCase()

        coVerify(exactly = 1) { beginSettingsEdit.invoke() }
        coVerify(exactly = 1) {
            writeChannel.invoke(
                1,
                DefaultContour.CHANNEL_NAME,
                DefaultContour.OPEN_PSK,
                ChannelPositionPrecision.DISABLED,
            )
        }
        coVerify(exactly = 1) { commitSettingsEdit.invoke() }
    }

    @Test
    fun `rewrites Emergency slot 1 when geo precision enabled`() = runTest {
        val openPskBytes = Base64.getDecoder().decode(DefaultContour.OPEN_PSK)
        val defaultPskBytes = Base64.getDecoder().decode(DefaultActiveContour.DEFAULT_PSK)
        val primary = Contour(
            id = DefaultActiveContour.ID,
            name = DefaultActiveContour.DISPLAY_NAME,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(
                meshtastic = MeshtasticChannel(
                    psk = DefaultActiveContour.DEFAULT_PSK,
                    channelHash = ContourHash.compute(DefaultActiveContour.CHANNEL_NAME, defaultPskBytes),
                ),
            ),
        )
        val primarySlot = NodeChannelSlot(
            index = 0,
            name = DefaultActiveContour.CHANNEL_NAME,
            psk = defaultPskBytes,
            isEnabled = true,
        )
        val emergencyWithGeo = NodeChannelSlot(
            index = 1,
            name = DefaultContour.CHANNEL_NAME,
            psk = openPskBytes,
            isEnabled = true,
            positionPrecision = ChannelPositionPrecision.ENABLED,
        )
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencyWithGeo))

        useCase()

        coVerify(exactly = 1) {
            writeChannel.invoke(
                1,
                DefaultContour.CHANNEL_NAME,
                DefaultContour.OPEN_PSK,
                ChannelPositionPrecision.DISABLED,
            )
        }
    }

    @Test
    fun `skips Emergency slot 1 write when primary is Emergency`() = runTest {
        coEvery { contourRepository.getPrimaryContourId() } returns DefaultContour.ID
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(DefaultContour.asContour()))

        useCase()

        coVerify(exactly = 0) { writeChannel.invoke(1, any(), any()) }
    }

    @Test
    fun `active non-primary contour on FreeSlot — writeChannel from slot 2`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        val bravo = makeContour("99", "Bravo")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary, bravo))
        every { resolveSlot.invoke(bravo, any(), match { it.containsAll(setOf(0, 1)) }, any()) } returns SlotResolution.FreeSlot(2)

        useCase()

        coVerify(exactly = 1) { writeChannel.invoke(2, "Bravo", pskBase64) }
    }

    @Test
    fun `inactive contour — not written`() = runTest {
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        val inactive = makeContour("99", "Inactive", isActive = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary, inactive))

        useCase()

        coVerify(exactly = 0) { writeChannel.invoke(2, any(), any()) }
    }

    @Test
    fun `broadcast mismatch — prepareNodeForAppDrivenBroadcast called inside session`() = runTest {
        coEvery { getPositionBroadcastSecs.invoke() } returns 900 // firmware default, differs from MAX_VALUE
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))

        useCase()

        coVerify(exactly = 1) { prepareNodeForAppDrivenBroadcast.invoke() }
        coVerify(exactly = 0) { disableNodePositionBroadcast.invoke() }
    }

    @Test
    fun `broadcast disabled desired — disableNodePositionBroadcast called`() = runTest {
        every { observeGpsBroadcastEnabled.invoke() } returns flowOf(false)
        coEvery { getPositionBroadcastSecs.invoke() } returns 60
        val primary = makeContour(DefaultActiveContour.ID.value, DefaultActiveContour.DISPLAY_NAME)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))

        useCase()

        coVerify(exactly = 1) { disableNodePositionBroadcast.invoke() }
        coVerify(exactly = 0) { prepareNodeForAppDrivenBroadcast.invoke() }
    }

    @Test
    fun `broadcast already correct — no broadcast write`() = runTest {
        coEvery { getPositionBroadcastSecs.invoke() } returns Int.MAX_VALUE // matches desired
        val openPskBytes = Base64.getDecoder().decode(DefaultContour.OPEN_PSK)
        val defaultPskBytes = Base64.getDecoder().decode(DefaultActiveContour.DEFAULT_PSK)
        val primary = Contour(
            id = DefaultActiveContour.ID,
            name = DefaultActiveContour.DISPLAY_NAME,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(
                meshtastic = MeshtasticChannel(
                    psk = DefaultActiveContour.DEFAULT_PSK,
                    channelHash = ContourHash.compute(DefaultActiveContour.CHANNEL_NAME, defaultPskBytes),
                ),
            ),
        )
        val primarySlot = NodeChannelSlot(index = 0, name = DefaultActiveContour.CHANNEL_NAME, psk = defaultPskBytes, isEnabled = true)
        val emergencySlot = NodeChannelSlot(
            index = 1, name = DefaultContour.CHANNEL_NAME, psk = openPskBytes, isEnabled = true,
            positionPrecision = ChannelPositionPrecision.DISABLED,
        )
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(primary))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        useCase()

        coVerify(exactly = 0) { prepareNodeForAppDrivenBroadcast.invoke() }
        coVerify(exactly = 0) { disableNodePositionBroadcast.invoke() }
    }
}
