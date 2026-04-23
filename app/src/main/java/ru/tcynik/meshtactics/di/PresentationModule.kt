package ru.tcynik.meshtactics.di

import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import ru.tcynik.meshtactics.presentation.feature.chat.ChatViewModel
import ru.tcynik.meshtactics.presentation.feature.groups.GroupsViewModel
import ru.tcynik.meshtactics.presentation.feature.main.MainViewModel
import ru.tcynik.meshtactics.presentation.feature.markers.MarkersViewModel
import ru.tcynik.meshtactics.presentation.feature.meshtest.MeshTestViewModel
import ru.tcynik.meshtactics.presentation.feature.node.NodeSettingsViewModel
import ru.tcynik.meshtactics.presentation.feature.node.NodeStatusViewModel
import ru.tcynik.meshtactics.presentation.feature.nodes.NodesViewModel
import ru.tcynik.meshtactics.domain.map.usecase.DeleteImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.HideImportedMapUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ImportMapFileUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveImportedMapsUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ObserveSelectedOverlaysUseCase
import ru.tcynik.meshtactics.domain.map.usecase.ToggleImportedMapSelectionUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ConnectToMeshDeviceUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.GetLastConnectedDeviceUseCase
import ru.tcynik.meshtactics.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.DeleteExpiredGeoMarksUseCase
import ru.tcynik.meshtactics.domain.marker.usecase.IngestReceivedGeoMarksUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.NodeProvisioningUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveLogicalChannelsUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.meshtactics.domain.mesh.usecase.ScanMeshDevicesUseCase
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
            observeSelectedOverlays = get<ObserveSelectedOverlaysUseCase>(),
            observeTotalUnreadChatCount = get(),
            scanDevices = get<ScanMeshDevicesUseCase>(),
            connectToDevice = get<ConnectToMeshDeviceUseCase>(),
            getLastConnectedDevice = get<GetLastConnectedDeviceUseCase>(),
            nodeProvisioning = get<NodeProvisioningUseCase>(),
            observeGeoMarks = get(),
            sendGeoMark = get(),
            ingestReceivedGeoMarks = get<IngestReceivedGeoMarksUseCase>(),
            deleteExpiredGeoMarks = get<DeleteExpiredGeoMarksUseCase>(),
            ingestReceivedChatMessages = get<IngestReceivedChatMessagesUseCase>(),
            observeLogicalChannels = get<ObserveLogicalChannelsUseCase>(),
            observeNodeChannels = get<ObserveNodeChannelsUseCase>(),
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
        )
    }
    viewModelOf(::UserSettingsViewModel)
    viewModelOf(::NodeSettingsViewModel)
    viewModelOf(::NodeStatusViewModel)
    viewModelOf(::MarkersViewModel)
    viewModelOf(::GroupsViewModel)

    // ── Legacy / prototype ───────────────────────────────────────────────────
    viewModel { NodesViewModel(get()) }
    viewModel {
        MeshTestViewModel(
            observeConnectionStatus = get(),
            scanDevices = get(),
            connectToDevice = get(),
            disconnectFromMesh = get(),
            observeNodes = get(),
            observeOurNode = get(),
            observeMessages = get(),
            sendMessage = get(),
            observeGeoNodes = get(),
            observeDeviceConfig = get(),
            requestDeviceConfig = get(),
            writeOwner = get(),
            writeChannel = get(),
            observeLocationConfig = get(),
            setProvideLocation = get(),
            writePositionConfig = get(),
            writeChannelPositionPrecision = get(),
            removeFixedPosition = get(),
        )
    }
}
