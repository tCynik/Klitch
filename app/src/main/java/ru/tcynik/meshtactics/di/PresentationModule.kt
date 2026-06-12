package ru.tcynik.meshtactics.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.tcynik.meshtactics.presentation.feature.chat.ChatViewModel
import ru.tcynik.meshtactics.presentation.feature.groups.GroupsViewModel
import ru.tcynik.meshtactics.presentation.feature.main.MainViewModel
import ru.tcynik.meshtactics.presentation.feature.marks.GeoMarksListViewModel
import ru.tcynik.meshtactics.presentation.feature.markers.MarkersViewModel
import ru.tcynik.meshtactics.presentation.feature.network.NetworkSettingsViewModel
import ru.tcynik.meshtactics.presentation.feature.network.NetworkViewModel
import ru.tcynik.meshtactics.presentation.feature.node.NodeSettingsViewModel
import ru.tcynik.meshtactics.presentation.feature.node.NodeStatusViewModel
import ru.tcynik.meshtactics.presentation.feature.nodes.NodesViewModel
import ru.tcynik.meshtactics.domain.map.usecase.DeleteImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.HideImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ImportMapFileUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveImportedMapsUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ToggleImportedMapSelectionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveContourNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveNodeSecurityConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.SyncEmergencyMuteUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.meshtactics.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.meshtactics.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.meshtactics.domain.mesh.repository.RebootStateRepository
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ReconnectAfterNodeRebootUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetScreenOrientationLockedUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetScreenOrientationModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.GetTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.ObserveTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetScreenOrientationLockedUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetScreenOrientationModeUseCase
import ru.tcynik.meshtactics.domain.settings.usecase.SetTileCacheModeUseCase
import ru.tcynik.meshtactics.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.meshtactics.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.meshtactics.domain.track.usecase.PauseTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ResumeTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.StartTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.StopTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.DiscardTrackRecordingUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ObserveRecordedTrackPointsUseCase
import ru.tcynik.meshtactics.domain.track.usecase.ToggleRecordedTrackVisibilityUseCase
import ru.tcynik.meshtactics.domain.track.usecase.DeleteRecordedTracksUseCase
import ru.tcynik.meshtactics.domain.track.usecase.UpdateTrackRecordingNameUseCase
import ru.tcynik.meshtactics.domain.track.usecase.UpdateTrackRecordingColorUseCase
import ru.tcynik.meshtactics.data.track.datasource.TrackSettingsDataSource
import ru.tcynik.meshtactics.presentation.feature.settings.SettingsViewModel
import ru.tcynik.meshtactics.presentation.feature.settings.UserSettingsViewModel

val presentationModule = module {

    // ── Main ────────────────────────────────────────────────────────────────
    viewModel {
        MainViewModel(
            getTileUrl = get(),
            getLastPosition = get(),
            saveLastPosition = get(),
            observeNodeMarkers = get(),
            observeConnectionStatus = get(),
            observeGpsStatus = get(),
            getMarkerSizeLevel = get(),
            observeMarkerSizeLevel = get(),
            getGeoMarkSizeLevel = get<GetGeoMarkSizeLevelUseCase>(),
            observeGeoMarkSizeLevel = get<ObserveGeoMarkSizeLevelUseCase>(),
            getShowGeoMarkNames = get<GetShowGeoMarkNamesUseCase>(),
            observeShowGeoMarkNames = get<ObserveShowGeoMarkNamesUseCase>(),
            observeNetworkEnabled = get<ObserveNetworkEnabledUseCase>(),
            observeSelectedOverlays = get<ObserveSelectedOverlaysUseCase>(),
            observeTotalUnreadChatCount = get(),
            scanDevices = get<ScanMeshDevicesUseCase>(),
            connectToDevice = get<ConnectToMeshDeviceUseCase>(),
            getLastConnectedDevice = get<GetLastConnectedDeviceUseCase>(),
            nodeProvisioning = get<NodeProvisioningUseCase>(),
            checkNodeSync = get<CheckNodeSyncUseCase>(),
            observeGeoMarks = get(),
            toggleGeoMarkVisibility = get<ToggleGeoMarkVisibilityUseCase>(),
            deleteGeoMarks = get(),
            sendGeoMark = get(),
            ingestReceivedGeoMarks = get<IngestReceivedGeoMarksUseCase>(),
            autoExpireGeoMarks = get<AutoExpireGeoMarksUseCase>(),
            ingestReceivedChatMessages = get<IngestReceivedChatMessagesUseCase>(),
            syncEmergencyMute = get<SyncEmergencyMuteUseCase>(),
            observeLogicalChannels = get<ObserveContoursUseCase>(),
            observeNodeChannels = get<ObserveNodeChannelsUseCase>(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            rebootStateRepository = get<RebootStateRepository>(),
            observeCallsignChanges = get<ObserveCallsignChangesUseCase>(),
            refreshNodePublicKey = get<RefreshNodePublicKeyUseCase>(),
            observeAppUser = get<ObserveAppUserUseCase>(),
            geoMarkPrefsRepository = get<GeoMarkPreferencesRepository>(),
            observeTrackRecordingState = get<ObserveTrackRecordingStateUseCase>(),
            startTrackRecording = get<StartTrackRecordingUseCase>(),
            pauseTrackRecording = get<PauseTrackRecordingUseCase>(),
            resumeTrackRecording = get<ResumeTrackRecordingUseCase>(),
            stopTrackRecording = get<StopTrackRecordingUseCase>(),
            discardTrackRecording = get<DiscardTrackRecordingUseCase>(),
            updateTrackRecordingName = get<UpdateTrackRecordingNameUseCase>(),
            updateTrackRecordingColor = get<UpdateTrackRecordingColorUseCase>(),
            trackSettingsDataSource = get<TrackSettingsDataSource>(),
            gpsRepository = get(),
            observeRecordedTracks = get<ObserveRecordedTracksUseCase>(),
            observeRecordedTrackPoints = get<ObserveRecordedTrackPointsUseCase>(),
            observeEmergencyMode = get<ObserveEmergencyModeUseCase>(),
            cancelEmergency = get<CancelEmergencyUseCase>(),
            triggerEmergency = get(),
        )
    }

    // ── Feature screens ──────────────────────────────────────────────────────
    viewModelOf(::ChatViewModel)
    viewModel {
        SettingsViewModel(
            repository = get(),
            observeImportedMaps = get<ObserveImportedMapsUseCase>(),
            importMapFile = get<ImportMapFileUseCase>(),
            hideImportedMap = get<HideImportedMapUseCase>(),
            deleteImportedMap = get<DeleteImportedMapUseCase>(),
            toggleImportedMapSelection = get<ToggleImportedMapSelectionUseCase>(),
            getTileCacheMode = get<GetTileCacheModeUseCase>(),
            observeTileCacheMode = get<ObserveTileCacheModeUseCase>(),
            setTileCacheMode = get<SetTileCacheModeUseCase>(),
            getScreenOrientationLocked = get<GetScreenOrientationLockedUseCase>(),
            getScreenOrientationMode = get<GetScreenOrientationModeUseCase>(),
            setScreenOrientationLocked = get<SetScreenOrientationLockedUseCase>(),
            setScreenOrientationMode = get<SetScreenOrientationModeUseCase>(),
        )
    }
    viewModel {
        UserSettingsViewModel(
            observeAppUser = get(),
            saveAppUser = get(),
            observeContours = get(),
            saveContour = get(),
            deleteContour = get(),
            setContourActive = get(),
            setPrimaryContour = get(),
            contourRepository = get(),
            observeNodeChannels = get(),
            beginSettingsEdit = get<BeginSettingsEditUseCase>(),
            commitSettingsEdit = get<CommitSettingsEditUseCase>(),
            writeChannel = get(),
            resolveSlot = get(),
            observeConnectionStatus = get(),
            channelSlotResolver = get(),
            confirmChannelSync = get<ConfirmChannelSyncUseCase>(),
            prepareNodeForAppDrivenBroadcast = get(),
            disableNodePositionBroadcast = get(),
            observeEmergencyMode = get(),
            triggerEmergency = get(),
            cancelEmergency = get(),
            checkContourSync = get<CheckNodeSyncUseCase>(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            disconnectFromMesh = get<DisconnectFromMeshUseCase>(),
            rebootNode = get<RebootNodeUseCase>(),
            reconnectAfterNodeReboot = get<ReconnectAfterNodeRebootUseCase>(),
            rebootStateRepository = get<RebootStateRepository>(),
            observeGpsBroadcastEnabled = get<ObserveGpsBroadcastEnabledUseCase>(),
            setGpsBroadcastEnabled = get<SetGpsBroadcastEnabledUseCase>(),
            observeDeviceConfig = get<ObserveDeviceConfigUseCase>(),
            writeOwner = get<WriteOwnerUseCase>(),
            checkOwnPkcHealth = get<CheckOwnPkcHealthUseCase>(),
            refreshNodePublicKeys = get<RefreshNodePublicKeysUseCase>(),
            regeneratePkcKeys = get<RegeneratePkcKeysUseCase>(),
            logger = get(),
        )
    }
    viewModel {
        NodeSettingsViewModel(
            observeNodeSecurityConfig = get<ObserveNodeSecurityConfigUseCase>(),
            observeConnectionStatus = get(),
            regeneratePkcKeys = get<RegeneratePkcKeysUseCase>(),
            rebootNode = get<RebootNodeUseCase>(),
            logger = get(),
        )
    }
    viewModelOf(::NodeStatusViewModel)
    viewModelOf(::MarkersViewModel)
    viewModel {
        GeoMarksListViewModel(
            observeGeoMarks = get(),
            observeContours = get(),
            observeMeshNodes = get(),
            toggleVisibility = get<ToggleGeoMarkVisibilityUseCase>(),
            deleteGeoMarks = get(),
            extendGeoMark = get(),
            sendGeoMark = get(),
            observeRecordedTracks = get<ObserveRecordedTracksUseCase>(),
            toggleTrackVisibility = get<ToggleRecordedTrackVisibilityUseCase>(),
            deleteRecordedTracks = get<DeleteRecordedTracksUseCase>(),
            logger = get(),
        )
    }
    viewModelOf(::GroupsViewModel)

    // ── Legacy / prototype ───────────────────────────────────────────────────
    viewModel { NodesViewModel(get()) }
    viewModel {
        NetworkViewModel(
            observeConnectionStatus = get(),
            scanDevices = get(),
            connectToDevice = get(),
            disconnectFromMesh = get(),
            observeNodes = get(),
            observeOurNode = get(),
            checkContourSync = get<CheckNodeSyncUseCase>(),
            observeNodeChannels = get<ObserveNodeChannelsUseCase>(),
            confirmChannelSync = get<ConfirmChannelSyncUseCase>(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            rebootStateRepository = get<RebootStateRepository>(),
            observeAppUser = get<ObserveAppUserUseCase>(),
            saveAppUser = get<SaveAppUserUseCase>(),
            observeNetworkEnabled = get(),
            setNetworkEnabled = get(),
            observeDeviceConfig = get(),
            logger = get(),
        )
    }
    viewModel {
        NetworkSettingsViewModel(
            observeConnectionStatus = get(),
            observeDeviceConfig = get(),
            requestDeviceConfig = get(),
            beginSettingsEdit = get<BeginSettingsEditUseCase>(),
            commitSettingsEdit = get<CommitSettingsEditUseCase>(),
            writeOwner = get(),
            writeChannel = get(),
            observeOurNode = get(),
            observeLocationConfig = get(),
            setProvideLocation = get(),
            writePositionConfig = get(),
            writeChannelPositionPrecision = get(),
            removeFixedPosition = get(),
            uiPrefs = get(),
            logger = get(),
        )
    }
}
