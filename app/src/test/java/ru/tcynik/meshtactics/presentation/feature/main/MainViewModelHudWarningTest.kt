package ru.tcynik.meshtactics.presentation.feature.main

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
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
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ContourHash
import ru.tcynik.meshtactics.domain.channel.model.ContourId
import ru.tcynik.meshtactics.domain.channel.model.ContourTransport
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticChannel
import ru.tcynik.meshtactics.domain.channel.model.NodeChannelSlot
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ObserveTotalUnreadChatCountUseCase
import ru.tcynik.meshtactics.domain.location.model.GpsStatusModel
import ru.tcynik.meshtactics.domain.location.usecase.ObserveGpsStatusUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.meshtactics.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
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
    private val getGeoMarkSizeLevel: GetGeoMarkSizeLevelUseCase = mockk()
    private val observeGeoMarkSizeLevel: ObserveGeoMarkSizeLevelUseCase = mockk()
    private val getShowGeoMarkNames: GetShowGeoMarkNamesUseCase = mockk()
    private val observeShowGeoMarkNames: ObserveShowGeoMarkNamesUseCase = mockk()
    private val observeSelectedOverlays: ObserveSelectedOverlaysUseCase = mockk()
    private val observeTotalUnreadChatCount: ObserveTotalUnreadChatCountUseCase = mockk()
    private val scanDevices: ScanMeshDevicesUseCase = mockk()
    private val connectToDevice: ConnectToMeshDeviceUseCase = mockk(relaxed = true)
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase = mockk()
    private val nodeProvisioning: NodeProvisioningUseCase = mockk(relaxed = true)
    private val observeGeoMarks: ObserveGeoMarksUseCase = mockk()
    private val toggleGeoMarkVisibility: ToggleGeoMarkVisibilityUseCase = mockk(relaxed = true)
    private val deleteGeoMarks: DeleteGeoMarksUseCase = mockk(relaxed = true)
    private val sendGeoMark: SendGeoMarkUseCase = mockk(relaxed = true)
    private val ingestReceivedGeoMarks: IngestReceivedGeoMarksUseCase = mockk()
    private val autoExpireGeoMarks: AutoExpireGeoMarksUseCase = mockk(relaxed = true)
    private val ingestReceivedChatMessages: IngestReceivedChatMessagesUseCase = mockk()
    private val observeLogicalChannels: ObserveContoursUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val checkNodeSync: CheckNodeSyncUseCase = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val rebootStateRepository: RebootStateRepository = mockk(relaxed = true)
    private val observeCallsignChanges: ObserveCallsignChangesUseCase = mockk()
    private val refreshNodePublicKey: RefreshNodePublicKeyUseCase = mockk(relaxed = true)
    private val geoMarkPrefsRepository: GeoMarkPreferencesRepository = mockk(relaxed = true)

    private val channelsFlow = MutableStateFlow<List<Contour>>(emptyList())
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
        every { getGeoMarkSizeLevel.invoke() } returns 5
        every { observeGeoMarkSizeLevel.invoke(any()) } returns flowOf(5)
        every { getShowGeoMarkNames.invoke() } returns false
        every { observeShowGeoMarkNames.invoke(any()) } returns flowOf(false)
        every { observeSelectedOverlays.invoke(any()) } returns flowOf(emptyList())
        every { observeTotalUnreadChatCount.invoke(any()) } returns flowOf(0)
        every { scanDevices.invoke(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { getLastConnectedDevice.invoke() } returns null
        every { observeGeoMarks.invoke(any()) } returns flowOf(emptyList())
        every { ingestReceivedGeoMarks.observe() } returns flowOf(Unit)
        every { autoExpireGeoMarks.observe() } returns flowOf(Unit)
        every { ingestReceivedChatMessages.observe() } returns flowOf(Unit)
        every { observeLogicalChannels.invoke(any()) } returns channelsFlow
        every { observeNodeChannels.invoke(any()) } returns nodeChannelsFlow
        every { syncStateRepository.syncRequired } returns MutableStateFlow(false)
        every { rebootStateRepository.isRebooting } returns MutableStateFlow(false)
        every { observeCallsignChanges.invoke(any()) } returns flowOf(0)
        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.InSync
        every { geoMarkPrefsRepository.observePreferences() } returns flowOf(GeoMarkFormPreferences())
        every { geoMarkPrefsRepository.observePresets() } returns flowOf(emptyList())
        viewModel = MainViewModel(
            getTileUrl = getTileUrl,
            getLastPosition = getLastPosition,
            saveLastPosition = saveLastPosition,
            observeNodeMarkers = observeNodeMarkers,
            observeConnectionStatus = observeConnectionStatus,
            observeGpsStatus = observeGpsStatus,
            getMarkerSizeLevel = getMarkerSizeLevel,
            observeMarkerSizeLevel = observeMarkerSizeLevel,
            getGeoMarkSizeLevel = getGeoMarkSizeLevel,
            observeGeoMarkSizeLevel = observeGeoMarkSizeLevel,
            getShowGeoMarkNames = getShowGeoMarkNames,
            observeShowGeoMarkNames = observeShowGeoMarkNames,
            observeSelectedOverlays = observeSelectedOverlays,
            observeTotalUnreadChatCount = observeTotalUnreadChatCount,
            scanDevices = scanDevices,
            connectToDevice = connectToDevice,
            getLastConnectedDevice = getLastConnectedDevice,
            nodeProvisioning = nodeProvisioning,
            checkNodeSync = checkNodeSync,
            observeGeoMarks = observeGeoMarks,
            toggleGeoMarkVisibility = toggleGeoMarkVisibility,
            deleteGeoMarks = deleteGeoMarks,
            sendGeoMark = sendGeoMark,
            ingestReceivedGeoMarks = ingestReceivedGeoMarks,
            autoExpireGeoMarks = autoExpireGeoMarks,
            ingestReceivedChatMessages = ingestReceivedChatMessages,
            observeLogicalChannels = observeLogicalChannels,
            observeNodeChannels = observeNodeChannels,
            syncStateRepository = syncStateRepository,
            rebootStateRepository = rebootStateRepository,
            observeCallsignChanges = observeCallsignChanges,
            refreshNodePublicKey = refreshNodePublicKey,
            geoMarkPrefsRepository = geoMarkPrefsRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeContour(name: String, psk: ByteArray): Contour {
        val pskBase64 = java.util.Base64.getEncoder().encodeToString(psk)
        val hash = ContourHash.compute(name, psk)
        return Contour(
            id = ContourId(UUID.randomUUID().toString()),
            name = name,
            description = null,
            expiration = null,
            exclusivityTime = null,
            isActive = true,
            transport = ContourTransport(meshtastic = MeshtasticChannel(psk = pskBase64, channelHash = hash)),
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
        channelsFlow.value = listOf(makeContour("Alpha", psk))
        nodeChannelsFlow.value = emptyList()
        runCurrent()
        assertFalse(viewModel.uiState.value.hasChannelOnNode)
    }

    @Test
    fun `channels exist and match non-zero slot — hasChannelOnNode is true`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeContour("Alpha", psk))
        nodeChannelsFlow.value = listOf(NodeChannelSlot(index = 1, name = "Alpha", psk = psk, isEnabled = true))
        runCurrent()
        assertTrue(viewModel.uiState.value.hasChannelOnNode)
    }

    @Test
    fun `slot 0 match only — hasChannelOnNode is false`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeContour("Alpha", psk))
        nodeChannelsFlow.value = listOf(NodeChannelSlot(index = 0, name = "Alpha", psk = psk, isEnabled = true))
        runCurrent()
        assertFalse(viewModel.uiState.value.hasChannelOnNode)
    }

    // ── HUD info slot ─────────────────────────────────────────────────────────

    @Test
    fun `connected and hasChannelOnNode false — HUD info slot shows warning`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeContour("Alpha", psk))
        nodeChannelsFlow.value = emptyList()
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        assertEquals("Настройте канал", infoSlot.content)
    }

    @Test
    fun `not connected and hasChannelOnNode false — HUD info slot is empty`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeContour("Alpha", psk))
        nodeChannelsFlow.value = emptyList()
        connectionStatusFlow.value = MeshConnectionStatus.Disconnected
        runCurrent()

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        assertNull(infoSlot.content)
    }

    @Test
    fun `connected and channel matches node slot — HUD does not show warning`() = runTest(testDispatcher) {
        val psk = byteArrayOf(0x01)
        channelsFlow.value = listOf(makeContour("Alpha", psk))
        nodeChannelsFlow.value = listOf(NodeChannelSlot(index = 1, name = "Alpha", psk = psk, isEnabled = true))
        connectionStatusFlow.value = connectedStatus
        runCurrent()

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        // HUD may show "Сопряжено с ..." briefly, but must NOT show the channel warning
        assertNotEquals("Настройте канал", infoSlot.content)
    }
}
