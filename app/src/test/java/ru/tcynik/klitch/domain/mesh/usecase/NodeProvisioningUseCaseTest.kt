package ru.tcynik.klitch.domain.mesh.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.domain.channel.model.ContourId
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.LocationConfigModel
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.logger.NoOpLogger

/** Covers the auto-config defaults the app pushes to the node — see project memory "ux-philosophy-autoconfig". */
class NodeProvisioningUseCaseTest {

    private val contourRepository: ContourRepository = mockk()
    private val observeContours: ObserveContoursUseCase = mockk()
    private val writeChannel: WriteChannelUseCase = mockk(relaxed = true)
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val resolveSlot: ResolveChannelSlotUseCase = mockk()
    private val observeOurNode: ObserveOurNodeUseCase = mockk()
    private val observeDeviceConfig: ObserveDeviceConfigUseCase = mockk()
    private val observeLocationConfig: ObserveLocationConfigUseCase = mockk()
    private val writePositionConfig: WritePositionConfigUseCase = mockk(relaxed = true)
    private val setProvideLocation: SetProvideLocationUseCase = mockk(relaxed = true)
    private val writeChannelPositionPrecision: WriteChannelPositionPrecisionUseCase = mockk(relaxed = true)
    private val removeFixedPosition: RemoveFixedPositionUseCase = mockk(relaxed = true)

    private val node: MeshNodeModel = mockk()
    private val deviceConfig: MeshDeviceConfigModel = mockk()

    private lateinit var useCase: NodeProvisioningUseCase

    private fun firmwareDefaultConfig(fixedPositionEnabled: Boolean = false) = LocationConfigModel(
        provideLocationToMesh = false,
        hasLocationPermission = true,
        gpsMode = GpsMode.DISABLED,
        fixedPositionEnabled = fixedPositionEnabled,
        broadcastIntervalSecs = 900,
        smartBroadcastEnabled = false,
        smartBroadcastMinDistanceM = 0,
        positionFlags = 0,
        primaryChannelPositionPrecision = 0,
    )

    private fun provisionedConfig(fixedPositionEnabled: Boolean = false) = firmwareDefaultConfig(fixedPositionEnabled).copy(
        provideLocationToMesh = true,
        broadcastIntervalSecs = Int.MAX_VALUE,
        positionFlags = 897,
        primaryChannelPositionPrecision = 32,
    )

    @Before
    fun setUp() {
        every { observeContours.invoke(NoParams) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(NoParams) } returns flowOf(emptyList())
        coEvery { contourRepository.getPrimaryContourId() } returns ContourId("primary")
        every { observeOurNode.invoke(NoParams) } returns flowOf(node)
        every { node.num } returns 42
        every { observeDeviceConfig.invoke(NoParams) } returns flowOf(deviceConfig)

        useCase = NodeProvisioningUseCase(
            contourRepository = contourRepository,
            observeContours = observeContours,
            writeChannel = writeChannel,
            observeNodeChannels = observeNodeChannels,
            resolveSlot = resolveSlot,
            observeOurNode = observeOurNode,
            observeDeviceConfig = observeDeviceConfig,
            observeLocationConfig = observeLocationConfig,
            writePositionConfig = writePositionConfig,
            setProvideLocation = setProvideLocation,
            writeChannelPositionPrecision = writeChannelPositionPrecision,
            removeFixedPosition = removeFixedPosition,
            logger = NoOpLogger(),
        )
    }

    @Test
    fun `firmware default node — preset written, location sharing and full precision turned on`() = runTest {
        every { observeLocationConfig.invoke(42) } returns flowOf(firmwareDefaultConfig())

        useCase.provision()

        coVerify(exactly = 1) { writePositionConfig.invoke(42, GpsMode.DISABLED, Int.MAX_VALUE, false, 0, 897) }
        coVerify(exactly = 1) { setProvideLocation.invoke(42, true) }
        coVerify(exactly = 1) { writeChannelPositionPrecision.invoke(42, 0, 32) }
        coVerify(exactly = 0) { removeFixedPosition.invoke(any()) }
    }

    @Test
    fun `already provisioned node — no writes, no node reboot`() = runTest {
        every { observeLocationConfig.invoke(42) } returns flowOf(provisionedConfig())

        useCase.provision()

        coVerify(exactly = 0) { writePositionConfig.invoke(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { setProvideLocation.invoke(any(), any()) }
        coVerify(exactly = 0) { writeChannelPositionPrecision.invoke(any(), any(), any()) }
    }

    @Test
    fun `legacy preset (1800s) — migrated to app-driven broadcast value`() = runTest {
        every { observeLocationConfig.invoke(42) } returns flowOf(
            provisionedConfig().copy(broadcastIntervalSecs = 1800, smartBroadcastEnabled = true)
        )

        useCase.provision()

        coVerify(exactly = 1) { writePositionConfig.invoke(42, GpsMode.DISABLED, Int.MAX_VALUE, false, 0, 897) }
    }

    @Test
    fun `stale fixed position — removed regardless of firmware-default state`() = runTest {
        every { observeLocationConfig.invoke(42) } returns flowOf(
            provisionedConfig(fixedPositionEnabled = true)
        )

        useCase.provision()

        coVerify(exactly = 1) { removeFixedPosition.invoke(42) }
    }
}
