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
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
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

    private val useCase = CheckNodeSyncUseCase(
        observeContours = observeContours,
        observeNodeChannels = observeNodeChannels,
        observeAppUser = observeAppUser,
        observeDeviceConfig = observeDeviceConfig,
    )

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = ""))
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

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — активный контур присутствует на ноде`() = runTest {
        val psk = byteArrayOf(0x01, 0x02)
        val contour = makeContour("Alpha", psk)
        val contourSlot = makeSlot(1, "Alpha", psk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot, contourSlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — неактивный контур не проверяется`() = runTest {
        val psk = byteArrayOf(0x05)
        val inactive = makeContour("Delta", psk, isActive = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(inactive))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    // ── NeedsSync ─────────────────────────────────────────────────────────────

    @Test
    fun `InSync — список каналов ноды пуст (данные ещё не пришли)`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(emptyList())

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 0 имеет неверное имя`() = runTest {
        val wrongSlot0 = emergencySlot.copy(name = "WrongName")
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongSlot0))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — slot 0 имеет неверный PSK`() = runTest {
        val wrongSlot0 = emergencySlot.copy(psk = byteArrayOf(0xFF.toByte()))
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(wrongSlot0))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — активный контур отсутствует на ноде`() = runTest {
        val psk = byteArrayOf(0x03)
        val contour = makeContour("Bravo", psk)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — контур есть на ноде но isEnabled=false`() = runTest {
        val psk = byteArrayOf(0x04)
        val contour = makeContour("Charlie", psk)
        val disabledSlot = makeSlot(1, "Charlie", psk, isEnabled = false)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot, disabledSlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — контур есть на ноде но position_precision = 0`() = runTest {
        val psk = byteArrayOf(0x06)
        val contour = makeContour("Echo", psk)
        val noPrecisionSlot = makeSlot(1, "Echo", psk, positionPrecision = 0)
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(listOf(contour))
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot, noPrecisionSlot))

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `NeedsSync — позывной приложения не совпадает с именем на ноде`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = "NewCallsign"))
        every { observeDeviceConfig.invoke(any<NoParams>()) } returns flowOf(
            MeshDeviceConfigModel(longName = "OldCallsign", shortName = "OLD", loraPreset = "", txPowerDbm = "", region = "", channels = emptyList())
        )

        assertEquals(NodeSyncResult.NeedsSync, useCase())
    }

    @Test
    fun `InSync — позывной приложения совпадает с именем на ноде`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))
        every { observeAppUser.invoke(any<NoParams>()) } returns flowOf(AppUser(displayName = "SameCallsign"))
        every { observeDeviceConfig.invoke(any<NoParams>()) } returns flowOf(
            MeshDeviceConfigModel(longName = "SameCallsign", shortName = "SC", loraPreset = "", txPowerDbm = "", region = "", channels = emptyList())
        )

        assertEquals(NodeSyncResult.InSync, useCase())
    }

    @Test
    fun `InSync — displayName пустой, owner check пропускается`() = runTest {
        every { observeContours.invoke(any<NoParams>()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any<NoParams>()) } returns flowOf(listOf(emergencySlot))

        assertEquals(NodeSyncResult.InSync, useCase())
    }
}
