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
import ru.tcynik.meshtactics.domain.mesh.usecase.CheckOwnPkcHealthUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.DisconnectFromMeshUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveCallsignChangesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveMeshNodesUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveNodeSecurityConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeyUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RefreshNodePublicKeysUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RegeneratePkcKeysUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
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
import ru.tcynik.meshtactics.domain.channel.usecase.SyncContoursOnConnectUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveDeviceConfigUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ObserveGpsBroadcastEnabledUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.RebootNodeUseCase
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
            observeLogicalChannels = get<ObserveContoursUseCase>(),
            observeNodeChannels = get<ObserveNodeChannelsUseCase>(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            rebootStateRepository = get<RebootStateRepository>(),
            observeCallsignChanges = get<ObserveCallsignChangesUseCase>(),
            refreshNodePublicKey = get<RefreshNodePublicKeyUseCase>(),
            observeAppUser = get<ObserveAppUserUseCase>(),
            geoMarkPrefsRepository = get<GeoMarkPreferencesRepository>(),
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
            writeChannel = get(),
            resolveSlot = get(),
            observeConnectionStatus = get(),
            channelSlotResolver = get(),
            syncContoursOnConnect = get(),
            enableNodePositionBroadcastReady = get(),
            disableNodePositionBroadcast = get(),
            observeEmergencyMode = get(),
            triggerEmergency = get(),
            cancelEmergency = get(),
            checkContourSync = get<CheckNodeSyncUseCase>(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            disconnectFromMesh = get<DisconnectFromMeshUseCase>(),
            rebootNode = get<RebootNodeUseCase>(),
            rebootStateRepository = get<RebootStateRepository>(),
            observeGpsBroadcastEnabled = get<ObserveGpsBroadcastEnabledUseCase>(),
            setGpsBroadcastEnabled = get<SetGpsBroadcastEnabledUseCase>(),
            observeDeviceConfig = get<ObserveDeviceConfigUseCase>(),
            writeOwner = get<WriteOwnerUseCase>(),
            checkOwnPkcHealth = get<CheckOwnPkcHealthUseCase>(),
            refreshNodePublicKeys = get<RefreshNodePublicKeysUseCase>(),
            regeneratePkcKeys = get<RegeneratePkcKeysUseCase>(),
        )
    }
    viewModel {
        NodeSettingsViewModel(
            observeNodeSecurityConfig = get<ObserveNodeSecurityConfigUseCase>(),
            observeConnectionStatus = get(),
            regeneratePkcKeys = get<RegeneratePkcKeysUseCase>(),
            rebootNode = get<RebootNodeUseCase>(),
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
            syncContoursOnConnect = get<SyncContoursOnConnectUseCase>(),
            rebootNode = get<RebootNodeUseCase>(),
            syncStateRepository = get<ContourSyncStateRepository>(),
            rebootStateRepository = get<RebootStateRepository>(),
            observeAppUser = get<ObserveAppUserUseCase>(),
            saveAppUser = get<SaveAppUserUseCase>(),
            observeNetworkEnabled = get(),
            setNetworkEnabled = get(),
            logger = get(),
        )
    }
    viewModel {
        NetworkSettingsViewModel(
            observeConnectionStatus = get(),
            observeDeviceConfig = get(),
            requestDeviceConfig = get(),
            writeOwner = get(),
            writeChannel = get(),
            observeOurNode = get(),
            observeLocationConfig = get(),
            setProvideLocation = get(),
            writePositionConfig = get(),
            writeChannelPositionPrecision = get(),
            removeFixedPosition = get(),
            logger = get(),
        )
    }
}
