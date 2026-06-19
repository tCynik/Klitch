package ru.tcynik.klitch.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.tcynik.klitch.presentation.feature.chat.ChatViewModel
import ru.tcynik.klitch.presentation.feature.groups.GroupsViewModel
import ru.tcynik.klitch.presentation.feature.main.ConnectionViewModel
import ru.tcynik.klitch.presentation.feature.main.EmergencyViewModel
import ru.tcynik.klitch.presentation.feature.main.GeoMarkViewModel
import ru.tcynik.klitch.presentation.feature.main.MainViewModel
import ru.tcynik.klitch.presentation.feature.main.TrackRecordingViewModel
import ru.tcynik.klitch.presentation.feature.marks.GeoMarksListViewModel
import ru.tcynik.klitch.presentation.feature.markers.MarkersViewModel
import ru.tcynik.klitch.presentation.feature.network.NetworkSettingsViewModel
import ru.tcynik.klitch.presentation.feature.network.NetworkViewModel
import ru.tcynik.klitch.presentation.feature.node.NodeSettingsViewModel
import ru.tcynik.klitch.presentation.feature.node.NodeStatusViewModel
import ru.tcynik.klitch.presentation.feature.nodes.NodesViewModel
import ru.tcynik.klitch.domain.map.usecase.DeleteImportedMapUseCase
import ru.tcynik.klitch.domain.map.usecase.HideImportedMapUseCase
import ru.tcynik.klitch.domain.map.usecase.ImportMapFileUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveImportedMapsUseCase
import ru.tcynik.klitch.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.klitch.domain.map.usecase.ToggleImportedMapSelectionUseCase
import ru.tcynik.klitch.domain.mesh.usecase.BeginSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.klitch.domain.mesh.usecase.CommitSettingsEditUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.klitch.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveContourNodesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveNodeSecurityConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.klitch.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.klitch.domain.chat.usecase.SyncEmergencyMuteUseCase
import ru.tcynik.klitch.domain.emergency.usecase.CancelEmergencyUseCase
import ru.tcynik.klitch.domain.emergency.usecase.ObserveEmergencyModeUseCase
import ru.tcynik.klitch.domain.emergency.usecase.TriggerEmergencyUseCase
import ru.tcynik.klitch.domain.marker.usecase.AutoExpireGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.klitch.domain.marker.usecase.ToggleGeoMarkVisibilityUseCase
import ru.tcynik.klitch.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.channel.usecase.CheckNodeSyncUseCase
import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository
import ru.tcynik.klitch.domain.marker.repository.GeoMarkPreferencesRepository
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.channel.usecase.ConfirmChannelSyncUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.RebootNodeUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ReconnectAfterNodeRebootUseCase
import ru.tcynik.klitch.domain.mesh.usecase.ScanMeshDevicesUseCase
import ru.tcynik.klitch.domain.mesh.usecase.SetGpsBroadcastEnabledUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteOwnerUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetScreenOrientationLockedUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetScreenOrientationModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.settings.usecase.GetTileCacheModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveGeoMarkSizeLevelUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveNetworkEnabledUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveShowGeoMarkNamesUseCase
import ru.tcynik.klitch.domain.settings.usecase.ObserveTileCacheModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetScreenOrientationLockedUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetScreenOrientationModeUseCase
import ru.tcynik.klitch.domain.settings.usecase.SetTileCacheModeUseCase
import ru.tcynik.klitch.domain.user.usecase.ObserveAppUserUseCase
import ru.tcynik.klitch.domain.user.usecase.SaveAppUserUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveTrackRecordingStateUseCase
import ru.tcynik.klitch.domain.track.usecase.PauseTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.ResumeTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.StartTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.StopTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.DiscardTrackRecordingUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.ObserveRecordedTrackPointsUseCase
import ru.tcynik.klitch.domain.track.usecase.ToggleRecordedTrackVisibilityUseCase
import ru.tcynik.klitch.domain.track.usecase.DeleteRecordedTracksUseCase
import ru.tcynik.klitch.domain.track.usecase.UpdateTrackRecordingNameUseCase
import ru.tcynik.klitch.domain.track.usecase.UpdateTrackRecordingColorUseCase
import ru.tcynik.klitch.domain.gps.usecase.ObserveGpsLocationUseCase
import ru.tcynik.klitch.domain.service.GpsServiceController
import ru.tcynik.klitch.domain.track.repository.TrackSettingsRepository
import ru.tcynik.klitch.presentation.feature.settings.SettingsViewModel
import ru.tcynik.klitch.presentation.feature.settings.UserSettingsViewModel

val presentationModule = module {

    // ── Main ────────────────────────────────────────────────────────────────
    viewModel {
        MainViewModel(
            getTileUrl = get(),
            getLastPosition = get(),
            saveLastPosition = get(),
            observeNodeMarkers = get(),
            observeGpsStatus = get(),
            getMarkerSizeLevel = get(),
            observeMarkerSizeLevel = get(),
            getGeoMarkSizeLevel = get<GetGeoMarkSizeLevelUseCase>(),
            observeGeoMarkSizeLevel = get<ObserveGeoMarkSizeLevelUseCase>(),
            getShowGeoMarkNames = get<GetShowGeoMarkNamesUseCase>(),
            observeShowGeoMarkNames = get<ObserveShowGeoMarkNamesUseCase>(),
            observeSelectedOverlays = get<ObserveSelectedOverlaysUseCase>(),
            observeTotalUnreadChatCount = get(),
            ingestReceivedChatMessages = get<IngestReceivedChatMessagesUseCase>(),
            observeRecordedTracks = get<ObserveRecordedTracksUseCase>(),
            observeRecordedTrackPoints = get<ObserveRecordedTrackPointsUseCase>(),
            observeTrackRecordingState = get<ObserveTrackRecordingStateUseCase>(),
        )
    }

    viewModel {
        EmergencyViewModel(
            observeEmergencyMode = get<ObserveEmergencyModeUseCase>(),
            triggerEmergency = get<TriggerEmergencyUseCase>(),
            cancelEmergency = get<CancelEmergencyUseCase>(),
            syncEmergencyMute = get<SyncEmergencyMuteUseCase>(),
        )
    }

    viewModel {
        ConnectionViewModel(
            observeConnectionStatus = get(),
            observeNetworkEnabled = get<ObserveNetworkEnabledUseCase>(),
            scanDevices = get<ScanMeshDevicesUseCase>(),
            connectToDevice = get<ConnectToMeshDeviceUseCase>(),
            getLastConnectedDevice = get<GetLastConnectedDeviceUseCase>(),
            nodeProvisioning = get<NodeProvisioningUseCase>(),
            checkNodeSync = get<CheckNodeSyncUseCase>(),
            observeNodeChannels = get<ObserveNodeChannelsUseCase>(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            rebootStateRepository = get<RebootStateRepository>(),
            observeCallsignChanges = get<ObserveCallsignChangesUseCase>(),
            refreshNodePublicKey = get<RefreshNodePublicKeyUseCase>(),
            observeAppUser = get<ObserveAppUserUseCase>(),
            gpsServiceController = get<GpsServiceController>(),
            observePositionSourceMode = get(),
        )
    }

    viewModel {
        GeoMarkViewModel(
            observeGeoMarks = get(),
            ingestReceivedGeoMarks = get<IngestReceivedGeoMarksUseCase>(),
            autoExpireGeoMarks = get<AutoExpireGeoMarksUseCase>(),
            observeContours = get<ObserveContoursUseCase>(),
            observeConnectionStatus = get(),
            toggleGeoMarkVisibility = get<ToggleGeoMarkVisibilityUseCase>(),
            deleteGeoMarks = get(),
            sendGeoMark = get(),
            geoMarkPrefsRepository = get<GeoMarkPreferencesRepository>(),
        )
    }

    viewModel {
        TrackRecordingViewModel(
            startTrackRecording = get<StartTrackRecordingUseCase>(),
            pauseTrackRecording = get<PauseTrackRecordingUseCase>(),
            resumeTrackRecording = get<ResumeTrackRecordingUseCase>(),
            stopTrackRecording = get<StopTrackRecordingUseCase>(),
            discardTrackRecording = get<DiscardTrackRecordingUseCase>(),
            updateTrackRecordingName = get<UpdateTrackRecordingNameUseCase>(),
            updateTrackRecordingColor = get<UpdateTrackRecordingColorUseCase>(),
            trackSettingsRepository = get<TrackSettingsRepository>(),
            observeGpsLocation = get<ObserveGpsLocationUseCase>(),
            observeTrackRecordingState = get<ObserveTrackRecordingStateUseCase>(),
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
            setDesiredGpsMode = get(),
            getGpsMode = get(),
            writeChannelPositionPrecision = get(),
            removeFixedPosition = get(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            confirmChannelSync = get(),
            uiPrefs = get(),
            logger = get(),
        )
    }
}
