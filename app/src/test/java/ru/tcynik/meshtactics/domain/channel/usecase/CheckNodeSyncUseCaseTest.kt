package ru.tcynik.meshtactics.domain.channel.usecase

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import ru.tcynik.meshtactics.logger.NoOpLogger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.data.channel.repository.FakeContourRepository
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultActiveContour
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.model.meshtasticChannelName
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.mesh.model.ChannelPositionPrecision
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.usecase.GetPositionBroadcastSecsUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import java.util.Base64
import java.util.UUID

class CheckNodeSyncUseCaseTest {

    private val observeContours: ObserveContoursUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val observeDeviceConfig: ObserveDeviceConfigUseCase = mockk()
    private val observeGpsBroadcastEnabled: ObserveGpsBroadcastEnabledUseCase = mockk()
    private val observeEmergencyMode: ObserveEmergencyModeUseCase = mockk()
    private val getPositionBroadcastSecs: GetPositionBroadcastSecsUseCase = mockk()
    private val contourRepo = FakeContourRepository()

    private val useCase = CheckNodeSyncUseCase(
        observeContours = observeContours,
        observeNodeChannels = observeNodeChannels,
        observeAppUser = observeAppUser,
        observeDeviceConfig = observeDeviceConfig,
        contourRepository = contourRepo,
        observeGpsBroadcastEnabled = observeGpsBroadcastEnabled,
        observeEmergencyMode = observeEmergencyMode,
        getPositionBroadcastSecs = getPositionBroadcastSecs,
        logger = NoOpLogger(),
    )

    // Default PSK shared by Basic and Emergency (AQ== = [0x01])
    private val defaultPsk = Base64.getDecoder().decode(DefaultContour.OPEN_PSK)

    // Slot 0 = Primary (Basic by default)
    private val primarySlot = makeSlot(0, DefaultActiveContour.CHANNEL_NAME, defaultPsk)

    // Slot 1 = Emergency (LongFast, geo toggles off when not in SOS)
    private val emergencySlot = makeSlot(
        1,
        DefaultContour.CHANNEL_NAME,
        defaultPsk,
        positionPrecision = ChannelPositionPrecision.DISABLED,
    )

    // Basic contour in DB (Primary)
    private val basicContour = makeContour(
        name = DefaultActiveContour.DISPLAY_NAME,
        psk = defaultPsk,
        id = DefaultActiveContour.ID,
    )

    @Before
    fun setUp() {
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = ""))
        // Default: broadcast enabled, no SOS, node already has correct broadcastSecs=60
        every { observeGpsBroadcastEnabled.invoke() } returns flowOf(true)
        every { observeEmergencyMode.invoke() } returns flowOf(false)
        coEvery { getPositionBroadcastSecs.invoke() } returns 60
        // Default: primary = Basic
        contourRepo.setPrimaryId(DefaultActiveContour.ID)
    }

    private fun makeContour(
        name: String,
        psk: ByteArray,
        isActive: Boolean = true,
        id: ContourId = ContourId(UUID.randomUUID().toString()),
    ): Contour {
        val pskBase64 = Base64.getEncoder().encodeToString(psk)
        val contour = Contour(
            id = id,
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = isActive,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = ContourHash("pending"))),
        )
        val hash = ContourHash.compute(meshtasticChannelName(contour), psk)
        return contour.copy(
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
    fun `InSync — slot 0 Primary и slot 1 Emergency совпадают, активных контуров нет`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — активный не-Primary контур присутствует на ноде в слоте 2+`() = runTest {
        val psk = byteArrayOf(0x01, 0x02)
        val contour = makeContour("Alpha", psk)
        val contourSlot = makeSlot(2, "Alpha", psk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour, contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot, contourSlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — неактивный контур не проверяется`() = runTest {
        val psk = byteArrayOf(0x05)
        val inactive = makeContour("Delta", psk, isActive = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour, inactive))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — список каналов ноды пуст (данные ещё не пришли)`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(emptyList())

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — SOS активен, Primary = Emergency, slot 0 = LongFast`() = runTest {
        contourRepo.setPrimaryId(DefaultContour.ID)
        val emergencyAsSlot0 = makeSlot(0, DefaultContour.CHANNEL_NAME, defaultPsk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencyAsSlot0))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — позывной приложения совпадает с именем на ноде`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = "SameCallsign"))
        every { observeDeviceConfig.invoke(any<NoParams>()) } returns flowOf(
            MeshDeviceConfigModel(longName = "SameCallsign", shortName = "SC", loraPreset = "", txPowerDbm = "", region = "", channels = emptyList())
        )

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — displayName пустой, owner check пропускается`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — position_broadcast_secs уже соответствует желаемому (60, broadcast enabled)`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))
        coEvery { getPositionBroadcastSecs.invoke() } returns 60

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — position_broadcast_secs уже MAX, broadcast disabled`() = runTest {
        every { observeGpsBroadcastEnabled.invoke() } returns flowOf(false)
        coEvery { getPositionBroadcastSecs.invoke() } returns Int.MAX_VALUE
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — getPositionBroadcastSecs null (конфиг не загружен, пропускаем проверку)`() = runTest {
        coEvery { getPositionBroadcastSecs.invoke() } returns null
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    // ── NeedsSync ─────────────────────────────────────────────────────────────

    @Test
    fun `NeedsSync — slot 0 имеет неверное имя (не Primary)`() = runTest {
        val wrongSlot0 = primarySlot.copy(name = "WrongName")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongSlot0, emergencySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 0 имя отличается только регистром`() = runTest {
        val wrongCaseSlot0 = makeSlot(0, DefaultActiveContour.DISPLAY_NAME, defaultPsk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongCaseSlot0, emergencySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 0 имеет неверный PSK`() = runTest {
        val wrongSlot0 = primarySlot.copy(psk = byteArrayOf(0xFF.toByte()))
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongSlot0, emergencySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 0 содержит Emergency вместо Primary (старый порядок)`() = runTest {
        val oldSlot0 = makeSlot(0, DefaultContour.CHANNEL_NAME, defaultPsk) // LongFast на slot 0
        val oldSlot1 = makeSlot(1, DefaultActiveContour.DISPLAY_NAME, defaultPsk) // Basic на slot 1
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(oldSlot0, oldSlot1))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 1 Emergency с включённой геопозицией`() = runTest {
        val emergencyWithGeo = makeSlot(1, DefaultContour.CHANNEL_NAME, defaultPsk, positionPrecision = 32)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencyWithGeo))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 1 не содержит Emergency (когда Primary не Emergency)`() = runTest {
        val wrongSlot1 = emergencySlot.copy(name = "SomeOtherChannel")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, wrongSlot1))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 1 отсутствует (Emergency не записан)`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — активный контур отсутствует на ноде`() = runTest {
        val psk = byteArrayOf(0x03)
        val contour = makeContour("Bravo", psk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour, contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — контур есть на ноде но isEnabled=false`() = runTest {
        val psk = byteArrayOf(0x04)
        val contour = makeContour("Charlie", psk)
        val disabledSlot = makeSlot(2, "Charlie", psk, isEnabled = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour, contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot, disabledSlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — контур есть на ноде но position_precision = 0`() = runTest {
        val psk = byteArrayOf(0x06)
        val contour = makeContour("Echo", psk)
        val noPrecisionSlot = makeSlot(2, "Echo", psk, positionPrecision = 0)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour, contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot, noPrecisionSlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — контур на slot 1 не считается (зарезервирован под Emergency)`() = runTest {
        val psk = byteArrayOf(0x07)
        val contour = makeContour("Foxtrot", psk)
        val slotOnReserved = makeSlot(1, "Foxtrot", psk) // slot 1 занят контуром, не Emergency
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour, contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, slotOnReserved))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — позывной приложения не совпадает с именем на ноде`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = "NewCallsign"))
        every { observeDeviceConfig.invoke(any<NoParams>()) } returns flowOf(
            MeshDeviceConfigModel(longName = "OldCallsign", shortName = "OLD", loraPreset = "", txPowerDbm = "", region = "", channels = emptyList())
        )

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — position_broadcast_secs не соответствует (900 вместо 60, broadcast enabled)`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))
        coEvery { getPositionBroadcastSecs.invoke() } returns 900

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — position_broadcast_secs=60 но broadcast disabled (нужно выключить)`() = runTest {
        every { observeGpsBroadcastEnabled.invoke() } returns flowOf(false)
        coEvery { getPositionBroadcastSecs.invoke() } returns 60
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — SOS активен, position_broadcast_secs=60 (нужно выключить)`() = runTest {
        every { observeEmergencyMode.invoke() } returns flowOf(true)
        coEvery { getPositionBroadcastSecs.invoke() } returns 60
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(basicContour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(primarySlot, emergencySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }
}
