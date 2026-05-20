package ru.tcynik.meshtactics.presentation.feature.main

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.ObserveTotalUnreadChatCountUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.meshtactics.domain.location.model.GpsSignalLevel
import ru.tcynik.meshtactics.domain.location.model.GpsStatusModel
import ru.tcynik.meshtactics.domain.location.usecase.ObserveGpsStatusUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.map.usecase.GetTileUrlUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveNodeMarkersUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.meshtactics.domain.map.usecase.SaveLastMapPositionUseCase
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkFormPreferences
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkShape
import ru.tcynik.meshtactics.domain.marker.model.GeoMarkType
import ru.tcynik.meshtactics.domain.marker.model.GeoPoint
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ObserveGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.SendGeoMarkUseCase
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
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase

private const val DOUBLE_TAP_WINDOW_MS = 300L

class MainViewModelMarkToolTest {

    // ── use case mocks ────────────────────────────────────────────────────────

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

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { getTileUrl.invoke() } returns ""
        every { getLastPosition.invoke() } returns null
        every { observeNodeMarkers.invoke(any()) } returns flowOf(emptyList())
        every { observeConnectionStatus.invoke(any()) } returns flowOf(MeshConnectionStatus.Disconnected)
        every { observeGpsStatus.invoke(any()) } returns flowOf(GpsStatusModel.None)
        every { getMarkerSizeLevel.invoke() } returns 5
        every { observeMarkerSizeLevel.invoke(any()) } returns flowOf(5)
        every { observeSelectedOverlays.invoke(any()) } returns flowOf(emptyList())
        every { observeTotalUnreadChatCount.invoke(any()) } returns flowOf(0)
        every { scanDevices.invoke(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { getLastConnectedDevice.invoke() } returns null
        every { observeGeoMarks.invoke(any()) } returns flowOf(emptyList())
        every { ingestReceivedGeoMarks.observe() } returns flowOf(Unit)
        every { autoExpireGeoMarks.observe() } returns flowOf(Unit)
        every { ingestReceivedChatMessages.observe() } returns flowOf(Unit)
        every { observeLogicalChannels.invoke(any()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any()) } returns flowOf(emptyList())
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
            observeSelectedOverlays = observeSelectedOverlays,
            observeTotalUnreadChatCount = observeTotalUnreadChatCount,
            scanDevices = scanDevices,
            connectToDevice = connectToDevice,
            getLastConnectedDevice = getLastConnectedDevice,
            nodeProvisioning = nodeProvisioning,
            checkNodeSync = checkNodeSync,
            observeGeoMarks = observeGeoMarks,
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

    // ── toggleMarkTool ────────────────────────────────────────────────────────

    @Test
    fun `toggleMarkTool — activates mark tool`() {
        viewModel.toggleMarkTool()
        assertTrue(viewModel.uiState.value.markToolActive)
    }

    @Test
    fun `toggleMarkTool twice — deactivates mark tool`() {
        viewModel.toggleMarkTool()
        viewModel.toggleMarkTool()
        assertFalse(viewModel.uiState.value.markToolActive)
    }

    @Test
    fun `toggleMarkTool deactivate — clears pending mark points`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.75, 37.62)
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)

        viewModel.toggleMarkTool()
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    // ── onMapClick ────────────────────────────────────────────────────────────

    @Test
    fun `onMapClick when tool inactive — does not add pending point`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.markToolActive)
        viewModel.onMapClick(55.75, 37.62)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapClick when tool active — adds pending point`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.75, 37.62)
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)
    }

    @Test
    fun `onMapClick — preserves tap coordinates in pending point`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620)
        val pt = viewModel.uiState.value.pendingMarkPoints.first()
        assertEquals(55.750, pt.latitude, 1e-9)
        assertEquals(37.620, pt.longitude, 1e-9)
    }

    @Test
    fun `onMapClick multiple times — accumulates points after each debounce`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)
        viewModel.onMapClick(55.751, 37.621)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)
        assertEquals(2, viewModel.uiState.value.pendingMarkPoints.size)
    }

    // ── onMapDoubleClick ──────────────────────────────────────────────────────

    @Test
    fun `onMapDoubleClick when tool inactive — does not send`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.markToolActive)
        viewModel.onMapDoubleClick(55.75, 37.62)
        coVerify(exactly = 0) { sendGeoMark(any()) }
    }

    @Test
    fun `onMapDoubleClick clears draft from preceding single tap`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.75, 37.62)
        viewModel.onMapDoubleClick(55.75, 37.62)
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapDoubleClick sends a POINT mark via sendGeoMark`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapDoubleClick(55.75, 37.62)
        coVerify(exactly = 1) { sendGeoMark(match { it.mark.type == GeoMarkType.POINT }) }
    }

    @Test
    fun `onMapDoubleClick TRACK — appends tap point then sends all vertices`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620)
        viewModel.onMapClick(55.751, 37.621)
        viewModel.onMapDoubleClick(55.752, 37.622)
        coVerify(exactly = 1) {
            sendGeoMark(match {
                it.mark.type == GeoMarkType.TRACK &&
                    it.mark.points.size == 3 &&
                    it.mark.points[2].latitude == 55.752
            })
        }
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapDoubleClick TRACK with no pending points — does not send, tap point stays in draft`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapDoubleClick(55.750, 37.620)
        coVerify(exactly = 0) { sendGeoMark(any()) }
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)
    }

    @Test
    fun `onMapDoubleClick TRACK with one pending point — adds second and sends`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620)
        viewModel.onMapDoubleClick(55.751, 37.621)
        coVerify(exactly = 1) {
            sendGeoMark(match {
                it.mark.type == GeoMarkType.TRACK && it.mark.points.size == 2
            })
        }
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `onMapDoubleClick — uses form color shape and tap coordinates`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkColor(3)
        viewModel.setMarkShape(GeoMarkShape.TRIANGLE)
        viewModel.onMapDoubleClick(55.750, 37.620)
        coVerify(exactly = 1) {
            sendGeoMark(match {
                it.mark.type == GeoMarkType.POINT &&
                    it.mark.color == 3 &&
                    it.mark.shape == GeoMarkShape.TRIANGLE &&
                    it.mark.points.single().let { p ->
                        p.latitude == 55.750 && p.longitude == 37.620
                    }
            })
        }
    }

    // ── onMapLongClick ────────────────────────────────────────────────────────

    @Test
    fun `onMapLongClick when tool inactive — emits no context menu event`() = runTest(testDispatcher) {
        viewModel.contextMenuEvent.test {
            viewModel.onMapLongClick(55.75, 37.62, 100f, 200f)
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun `onMapLongClick near pending point — emits context menu event with correct index`() =
        runTest(testDispatcher) {
            viewModel.toggleMarkTool()
            viewModel.onMapClick(55.750, 37.620)
            advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)

            viewModel.contextMenuEvent.test {
                // Long-tap within ~10m of the pending point
                viewModel.onMapLongClick(55.7501, 37.6201, 150f, 250f)
                val event = awaitItem()
                assertEquals(0, event.pointIndex)
                assertEquals(150f, event.screenX)
                assertEquals(250f, event.screenY)
                cancel()
            }
        }

    @Test
    fun `onMapLongClick far from pending point — emits no context menu event`() =
        runTest(testDispatcher) {
            viewModel.toggleMarkTool()
            viewModel.onMapClick(55.750, 37.620)
            advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)

            var received = false
            val collectJob = backgroundScope.launch {
                viewModel.contextMenuEvent.collect { received = true }
            }
            viewModel.onMapLongClick(55.760, 37.630, 100f, 200f)
            runCurrent()
            assertFalse(received)
            collectJob.cancel()
        }

    // ── sendPendingMark ───────────────────────────────────────────────────────

    @Test
    fun `sendPendingMark — clears pending points`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)
        assertEquals(1, viewModel.uiState.value.pendingMarkPoints.size)

        viewModel.sendPendingMark()
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }

    @Test
    fun `sendPendingMark single point — sends POINT type`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)

        viewModel.sendPendingMark()
        coVerify(exactly = 1) { sendGeoMark(match { it.mark.type == GeoMarkType.POINT }) }
    }

    @Test
    fun `sendPendingMark two points — sends TRACK type`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)
        viewModel.onMapClick(55.751, 37.621)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)

        viewModel.sendPendingMark()
        coVerify(exactly = 1) { sendGeoMark(match { it.mark.type == GeoMarkType.TRACK }) }
    }

    @Test
    fun `sendPendingMark when empty — does not call sendGeoMark`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.sendPendingMark()
        coVerify(exactly = 0) { sendGeoMark(any()) }
    }

    // ── deletePendingPoint ────────────────────────────────────────────────────

    @Test
    fun `deletePendingPoint — removes point at specified index`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.setMarkType(GeoMarkType.TRACK)
        viewModel.onMapClick(55.750, 37.620)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)
        viewModel.onMapClick(55.751, 37.621)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)
        assertEquals(2, viewModel.uiState.value.pendingMarkPoints.size)

        viewModel.deletePendingPoint(0)

        val remaining = viewModel.uiState.value.pendingMarkPoints
        assertEquals(1, remaining.size)
        assertEquals(55.751, remaining[0].latitude, 1e-9)
    }

    @Test
    fun `deletePendingPoint last point — list becomes empty`() = runTest(testDispatcher) {
        viewModel.toggleMarkTool()
        viewModel.onMapClick(55.750, 37.620)
        advanceTimeBy(DOUBLE_TAP_WINDOW_MS + 10)

        viewModel.deletePendingPoint(0)
        assertTrue(viewModel.uiState.value.pendingMarkPoints.isEmpty())
    }
}
