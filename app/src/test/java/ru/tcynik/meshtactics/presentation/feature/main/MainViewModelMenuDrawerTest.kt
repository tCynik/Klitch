package ru.tcynik.meshtactics.presentation.feature.main

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ru.tcynik.meshtactics.domain.channel.model.NodeSyncResult
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.SyncEmergencyMuteUseCase
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
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.user.model.AppUser
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.data.track.datasource.TrackSettingsDataSource
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.mesh.model.NodeSyncCyclePhase
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingSettings
import ru.tcynik.meshtactics.domain.track.model.TrackRecordingState
import ru.tcynik.meshtactics.domain.track.usecase.DiscardTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveRecordedTrackPointsUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.meshtactics.domain.track.usecase.PauseTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ResumeTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.StartTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.StopTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.UpdateTrackRecordingColorUseCase
import ru.tcynik.meshtactics.domain.track.usecase.UpdateTrackRecordingNameUseCase

class MainViewModelMenuDrawerTest {

    // ── use case mocks ────────────────────────────────────────────────────────

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
    private val observeNetworkEnabled: ObserveNetworkEnabledUseCase = mockk()
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
    private val syncEmergencyMute: SyncEmergencyMuteUseCase = mockk()
    private val observeLogicalChannels: ObserveContoursUseCase = mockk()
    private val observeNodeChannels: ObserveNodeChannelsUseCase = mockk()
    private val checkNodeSync: CheckNodeSyncUseCase = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk(relaxed = true)
    private val rebootStateRepository: RebootStateRepository = mockk(relaxed = true)
    private val observeCallsignChanges: ObserveCallsignChangesUseCase = mockk()
    private val refreshNodePublicKey: RefreshNodePublicKeyUseCase = mockk(relaxed = true)
    private val geoMarkPrefsRepository: GeoMarkPreferencesRepository = mockk(relaxed = true)
    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val observeTrackRecordingState: ObserveTrackRecordingStateUseCase = mockk()
    private val startTrackRecording: StartTrackRecordingUseCase = mockk(relaxed = true)
    private val pauseTrackRecording: PauseTrackRecordingUseCase = mockk(relaxed = true)
    private val resumeTrackRecording: ResumeTrackRecordingUseCase = mockk(relaxed = true)
    private val stopTrackRecording: StopTrackRecordingUseCase = mockk(relaxed = true)
    private val discardTrackRecording: DiscardTrackRecordingUseCase = mockk(relaxed = true)
    private val updateTrackRecordingName: UpdateTrackRecordingNameUseCase = mockk(relaxed = true)
    private val updateTrackRecordingColor: UpdateTrackRecordingColorUseCase = mockk(relaxed = true)
    private val trackSettingsDataSource: TrackSettingsDataSource = mockk()
    private val gpsRepository: GpsRepository = mockk()
    private val observeRecordedTracks: ObserveRecordedTracksUseCase = mockk()
    private val observeRecordedTrackPoints: ObserveRecordedTrackPointsUseCase = mockk()

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
        every { getGeoMarkSizeLevel.invoke() } returns 5
        every { observeGeoMarkSizeLevel.invoke(any()) } returns flowOf(5)
        every { getShowGeoMarkNames.invoke() } returns false
        every { observeShowGeoMarkNames.invoke(any()) } returns flowOf(false)
        every { observeNetworkEnabled.invoke(any()) } returns flowOf(true)
        every { observeSelectedOverlays.invoke(any()) } returns flowOf(emptyList())
        every { observeTotalUnreadChatCount.invoke(any()) } returns flowOf(0)
        every { scanDevices.invoke(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { getLastConnectedDevice.invoke() } returns null
        every { observeGeoMarks.invoke(any()) } returns flowOf(emptyList())
        every { ingestReceivedGeoMarks.observe() } returns flowOf(Unit)
        every { autoExpireGeoMarks.observe() } returns flowOf(Unit)
        every { ingestReceivedChatMessages.observe() } returns flowOf(Unit)
        every { syncEmergencyMute.observe() } returns flowOf(Unit)
        every { observeLogicalChannels.invoke(any()) } returns flowOf(emptyList())
        every { observeNodeChannels.invoke(any()) } returns flowOf(emptyList())
        every { syncStateRepository.syncRequired } returns MutableStateFlow(false)
        every { rebootStateRepository.isRebooting } returns MutableStateFlow(false)
        every { rebootStateRepository.syncCyclePhase } returns MutableStateFlow(NodeSyncCyclePhase.Idle)
        every { observeTrackRecordingState.invoke(any()) } returns flowOf(TrackRecordingState.Idle)
        every { trackSettingsDataSource.observeSettings() } returns flowOf(TrackRecordingSettings())
        every { gpsRepository.location } returns MutableStateFlow(null)
        every { observeRecordedTracks.invoke(any()) } returns flowOf(emptyList())
        every { observeRecordedTrackPoints.invoke(any()) } returns flowOf(emptyList())
        every { observeCallsignChanges.invoke(any()) } returns flowOf(0)
        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.InSync
        every { geoMarkPrefsRepository.observePreferences() } returns flowOf(GeoMarkFormPreferences())
        every { geoMarkPrefsRepository.observePresets() } returns flowOf(emptyList())
        every { observeAppUser.invoke(any()) } returns flowOf(AppUser(displayName = "Alpha"))
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
            observeNetworkEnabled = observeNetworkEnabled,
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
            syncEmergencyMute = syncEmergencyMute,
            observeLogicalChannels = observeLogicalChannels,
            observeNodeChannels = observeNodeChannels,
            syncStateRepository = syncStateRepository,
            rebootStateRepository = rebootStateRepository,
            observeCallsignChanges = observeCallsignChanges,
            refreshNodePublicKey = refreshNodePublicKey,
            observeAppUser = observeAppUser,
            geoMarkPrefsRepository = geoMarkPrefsRepository,
            observeTrackRecordingState = observeTrackRecordingState,
            startTrackRecording = startTrackRecording,
            pauseTrackRecording = pauseTrackRecording,
            resumeTrackRecording = resumeTrackRecording,
            stopTrackRecording = stopTrackRecording,
            discardTrackRecording = discardTrackRecording,
            updateTrackRecordingName = updateTrackRecordingName,
            updateTrackRecordingColor = updateTrackRecordingColor,
            trackSettingsDataSource = trackSettingsDataSource,
            gpsRepository = gpsRepository,
            observeRecordedTracks = observeRecordedTracks,
            observeRecordedTrackPoints = observeRecordedTrackPoints,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── toggleMenuDrawer ──────────────────────────────────────────────────────

    @Test
    fun `toggleMenuDrawer — opens drawer`() {
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)
    }

    @Test
    fun `toggleMenuDrawer twice — closes drawer`() {
        viewModel.toggleMenuDrawer()
        viewModel.toggleMenuDrawer()
        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }

    // ── menuDrawerUiState ─────────────────────────────────────────────────────

    @Test
    fun `menuDrawerUiState — isOpen reflects toggle`() = runTest(testDispatcher) {
        viewModel.menuDrawerUiState.test {
            assertFalse(awaitItem().isOpen)
            viewModel.toggleMenuDrawer()
            assertTrue(awaitItem().isOpen)
            viewModel.toggleMenuDrawer()
            assertFalse(awaitItem().isOpen)
            cancel()
        }
    }

    // ── drawer item onClick ───────────────────────────────────────────────────

    @Test
    fun `drawer radio onClick — invokes nav callback and closes drawer`() {
        var radioCalled = false
        viewModel.provideNavCallbacks(HudNavCallbacks(onRadioClick = { radioCalled = true }))
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)

        viewModel.menuDrawerUiState.value.items.first { it.label == "радио" }.onClick()

        assertTrue(radioCalled)
        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }

    @Test
    fun `drawer main settings onClick — invokes nav callback and closes drawer`() {
        var settingsCalled = false
        viewModel.provideNavCallbacks(HudNavCallbacks(onMainSettingsClick = { settingsCalled = true }))
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)

        viewModel.menuDrawerUiState.value.items.first { it.label == "Главная" }.onClick()

        assertTrue(settingsCalled)
        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }

    @Test
    fun `drawer onDismiss — closes drawer`() {
        viewModel.toggleMenuDrawer()
        assertTrue(viewModel.uiState.value.menuDrawerOpen)

        viewModel.menuDrawerUiState.value.onDismiss()

        assertFalse(viewModel.uiState.value.menuDrawerOpen)
    }
}
