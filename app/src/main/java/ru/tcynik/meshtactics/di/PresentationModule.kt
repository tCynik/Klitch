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
import ru.tcynik.meshtactics.presentation.feature.settings.SettingsViewModel

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
            observeTotalUnreadChatCount = get(),
        )
    }

    // ── Feature screens ──────────────────────────────────────────────────────
    viewModelOf(::ChatViewModel)
    viewModelOf(::SettingsViewModel)
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
