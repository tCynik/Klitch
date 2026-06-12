package ru.tcynik.meshtactics.presentation.feature.main

import androidx.lifecycle.ViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceParams
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveConnectionStatusUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetMarkerSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetShowGeoMarkNamesUseCase
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

class MainViewModelCallsignTest {

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
    private val observeAppUser: ObserveAppUserUseCase = mockk()
    private val geoMarkPrefsRepository: GeoMarkPreferencesRepository = mockk(relaxed = true)
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

    private val connectionStatusFlow = MutableStateFlow<MeshConnectionStatus>(MeshConnectionStatus.Disconnected)
    private val appUserFlow = MutableStateFlow(AppUser(displayName = ""))

    private val lastDevice = MeshDeviceModel(address = "AA:BB:CC:DD:EE:FF", name = "TestNode", rssi = -70)

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
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
        every { observeNetworkEnabled.invoke(any()) } returns flowOf(true)
        every { observeSelectedOverlays.invoke(any()) } returns flowOf(emptyList())
        every { observeTotalUnreadChatCount.invoke(any()) } returns flowOf(0)
        every { scanDevices.invoke(any()) } returns flow { kotlinx.coroutines.awaitCancellation() }
        every { getLastConnectedDevice.invoke() } returns lastDevice
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
        every { observeCallsignChanges.invoke(any()) } returns emptyFlow()
        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.InSync
        coEvery { connectToDevice.invoke(any()) } returns Unit
        every { geoMarkPrefsRepository.observePreferences() } returns flowOf(GeoMarkFormPreferences())
        every { geoMarkPrefsRepository.observePresets() } returns flowOf(emptyList())
        every { observeAppUser.invoke(any()) } returns appUserFlow
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized) {
            val onCleared = ViewModel::class.java.getDeclaredMethod("onCleared")
            onCleared.isAccessible = true
            onCleared.invoke(viewModel)
        }
        Dispatchers.resetMain()
    }

    private fun createViewModel() {
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

    @Test
    fun `пустой позывной — callsignRequired true`() {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        assertTrue(viewModel.uiState.value.callsignRequired)
    }

    @Test
    fun `пустой позывной — авто-подключение пропускается`() {
        coEvery { connectToDevice.invoke(any()) } coAnswers {
            fail("auto-connect must be skipped when callsign is blank")
        }
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
    }

    @Test
    fun `пустой позывной при Scanning — HUD показывает установите позывной`() {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        connectionStatusFlow.value = MeshConnectionStatus.Scanning

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        assertEquals("установите позывной", infoSlot.content)
    }

    @Test
    fun `пустой позывной при Disconnected — HUD info пустой`() {
        appUserFlow.value = AppUser(displayName = "")
        createViewModel()
        connectionStatusFlow.value = MeshConnectionStatus.Disconnected

        val infoSlot = viewModel.hudConfig.value.right.rows.first().info
        assertNull(infoSlot.content)
    }

    @Test
    fun `непустой позывной — callsignRequired false и авто-подключение выполняется`() {
        appUserFlow.value = AppUser(displayName = "Alpha")
        createViewModel()
        assertFalse(viewModel.uiState.value.callsignRequired)
        coVerify { connectToDevice.invoke(ConnectToMeshDeviceParams(lastDevice.address, lastDevice.name)) }
    }

    @Test
    fun `повторное подключение с InSync сбрасывает syncRequired через clear`() {
        val connected = MeshConnectionStatus.Connected(
            nodeId = "!aabbccdd",
            shortName = "TS",
            deviceName = "Meshtastic TS",
            rssi = -70,
            batteryLevel = 80,
        )
        appUserFlow.value = AppUser(displayName = "Alpha")
        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.NeedsSync
        createViewModel()

        connectionStatusFlow.value = connected
        coVerify { syncStateRepository.setSyncRequired(true) }

        connectionStatusFlow.value = MeshConnectionStatus.Disconnected

        coEvery { checkNodeSync.invoke() } returns NodeSyncResult.InSync
        connectionStatusFlow.value = connected

        verify { syncStateRepository.clear() }
    }
}
