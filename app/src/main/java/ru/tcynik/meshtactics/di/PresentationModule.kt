package ru.tcynik.meshtactics.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.tcynik.meshtactics.presentation.feature.meshtest.MeshTestViewModel
import ru.tcynik.meshtactics.presentation.feature.nodes.NodesViewModel

val presentationModule = module {
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
            observePacketLog = get(),
            observeDeviceConfig = get(),
            requestDeviceConfig = get(),
            writeOwner = get(),
            writeChannel = get(),
        )
    }
}
