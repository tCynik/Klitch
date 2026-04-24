package ru.tcynik.meshtactics.presentation.feature.main

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.ChannelMetadata
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ObserveTotalUnreadChatCountUseCase
import ru.tcynik.meshtactics.domain.location.model.GpsStatusModel
import ru.tcynik.meshtactics.domain.location.usecase.ObserveGpsStatusUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteExpiredGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import java.util.UUID

class MainViewModelHudWarningTest {

    private val getTileUrl: GetTileUrlUseCase = mockk()
    private val getLastPosition: GetLastMapPositionUseCase = mockk()
    private val saveLastPosition: SaveLastMapPositionUseCase = mockk(relaxed = true)
    private val observeNodeMarkers: ObserveNodeMarkersUseCase = mockk()
    private val observeConnectionStatus: ObserveConnectionStatusUseCase = mockk()
    private val observeGpsStatus: ObserveGpsStatusUseCase = mockk()
    private val getMarkerSizeLevel: GetMarkerSizeLevelUseCase = mockk()
    private val observeMarkerSizeLevel: ObserveMarkerSizeLevelUseCase = mockk()
    private val observeSelectedOverlays: ObserveSelectedOverlaysUseCase = mockk()
    private val observeTotalUnreadChatCount: ObserveTotalUnreadChatCountUseCase = mockk()
    private val scanDevices: ScanMeshDevicesUseCase = mockk()
    private val connectToDevice: ConnectToMeshDeviceUseCase = mockk(relaxed = true)
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase = mockk()
    private val nodeProvisioning: NodeProvisioningUseCase = mockk(relaxed = true)
    private val observeGeoMarks: ObserveGeoMarksUseCase = mockk()
    private val sendGeoMark: SendGeoMarkUseCase = mockk(relaxed = true)
    private val ingestReceivedGeoMarks: IngestReceivedGeoMarksUseCase = mockk()
    private val deleteExpiredGeoMarks: DeleteExpiredGeoMarksUseCase = mockk(relaxed = true)
    private val ingestReceivedChatMessages: IngestReceivedChatMessagesUseCase = mockk()
    private val observeLogicalChannels: ObserveLogicalChannelsUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()

    private val channelsFlow = MutableStateFlow<List<LogicalChannel>>(emptyList())
    private val nodeChannelsFlow = MutableStateFlow<List<NodeChannelSlot>>(emptyList())
    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getTileUrl.invoke() } returns ""
        every { getLastPosition.invoke() } returns null
        every { observeNodeMarkers.invoke(any()) } returns flowOf(emptyList())
        every { observeConnectionStatus.invoke(any()) } returns connectionStatusFlow
        every { observeGpsStatus.invoke(any()) } returns flowOf(GpsStatusModel.None)
        every { getMarkerSizeLevel.invoke() } returns 5
        every { observeMarkerSizeLevel.invoke(any()) } returns flowOf(5)
        every { observeSelectedOverlays.invoke(any()) } returns flowOf(emptyList())
        every { observeTotalUnreadChatCount.invoke(any()) } returns flowOf(0)
        every { scanDevices.invoke(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { getLastConnectedDevice.invoke() } returns null
        every { observeGeoMarks.invoke(any()) } returns flowOf(emptyList())
        every { ingestReceivedGeoMarks.observe() } returns flowOf(Unit)
        every { ingestReceivedChatMessages.observe() } returns flowOf(Unit)
        every { observeLogicalChannels.invoke(any()) } returns channelsFlow
        every { observeNodeChannels.invoke(any()) } returns nodeChannelsFlow
        viewModel = MainViewModel(
            getTileUrl = getTileUrl,
            getLastPosition = getLastPosition,
            saveLastPosition = saveLastPosition,
            observeNodeMarkers = observeNodeMarkers,
            observeConnectionStatus = observeConnectionStatus,
            observeGpsStatus = observeGpsStatus,
            getMarkerSizeLevel = getMarkerSizeLevel,
            observeMarkerSizeLevel = observeMarkerSizeLevel,
            observeSelectedOverlays = observeSelectedOverlays,
            observeTotalUnreadChatCount = observeTotalUnreadChatCount,
            scanDevices = scanDevices,
            connectToDevice = connectToDevice,
            getLastConnectedDevice = getLastConnectedDevice,
            nodeProvisioning = nodeProvisioning,
            observeGeoMarks = observeGeoMarks,
            sendGeoMark = sendGeoMark,
            ingestReceivedGeoMarks = ingestReceivedGeoMarks,
            deleteExpiredGeoMarks = deleteExpiredGeoMarks,
            ingestReceivedChatMessages = ingestReceivedChatMessages,
            observeLogicalChannels = observeLogicalChannels,
            observeNodeChannels = observeNodeChannels,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeChannel(name: String, psk: ByteArray): LogicalChannel {
        val hash = LogicalChannelHash.compute(name, psk)
        return LogicalChannel(
            id = LogicalChannelId(UUID.randomUUID().toString()),
            metadata = ChannelMetadata(name = name),
            transports = listOf(MeshtasticBinding(psk = psk, channelHash = hash)),
            isAutoSync = false,
        )
    }

    private val connectedStatus = MeshConnectionStatus.Connected(
        nodeId = "!aabbccdd",
        shortName = "ТЕ",
        rssi = -70,
        batteryLevel = 80,
    )

    // ── hasChannelOnNode ──────────────────────────────────────────────────────

    @Test
    fun `empty channels — hasChannelOnNode is true`() = runTest(testDispatcher) {
        assertTrue(viewModel.uiState.value.hasChannelOnNode)
    }

    @Test
    fun `channels exist no matching node slot — hasChannelOnNode is false`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeChannel("Alpha", psk))
        nodeChannelsFlow.value = emptyList()
        runCurrent()
        assertFalse(viewModel.uiState.value.hasChannelOnNode)
    }

    @Test
    fun `channels exist and match non-zero slot — hasChannelOnNode is true`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeChannel("Alpha", psk))
        nodeChannelsFlow.value = listOf(NodeChannelSlot(index = 1, name = "Alpha", psk = psk, isEnabled = true))
        runCurrent()
        assertTrue(viewModel.uiState.value.hasChannelOnNode)
    }

    @Test
    fun `slot 0 match only — hasChannelOnNode is false`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeChannel("Alpha", psk))
        nodeChannelsFlow.value = listOf(NodeChannelSlot(index = 0, name = "Alpha", psk = psk, isEnabled = true))
        runCurrent()
        assertFalse(viewModel.uiState.value.hasChannelOnNode)
    }

    // ── HUD info slot ─────────────────────────────────────────────────────────

    @Test
    fun `connected and hasChannelOnNode false — HUD info slot shows warning`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeChannel("Alpha", psk))
        nodeChannelsFlow.value = emptyList()
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        assertEquals("Настройте канал", infoSlot.content)
    }

    @Test
    fun `not connected and hasChannelOnNode false — HUD info slot is empty`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeChannel("Alpha", psk))
        nodeChannelsFlow.value = emptyList()
        connectionStatusFlow.value = MeshConnectionStatus.Disconnected
        runCurrent()

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        assertNull(infoSlot.content)
    }

    @Test
    fun `connected and channel matches node slot — HUD does not show warning`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeChannel("Alpha", psk))
        nodeChannelsFlow.value = listOf(NodeChannelSlot(index = 1, name = "Alpha", psk = psk, isEnabled = true))
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        // HUD may show "Сопряжено с ..." briefly, but must NOT show the channel warning
        assertNotEquals("Настройте канал", infoSlot.content)
    }
}
