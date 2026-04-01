package ru.tcynik.mymesh1.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import ru.tcynik.mymesh1.presentation.feature.meshtest.MeshTestViewModel
import ru.tcynik.mymesh1.presentation.feature.nodes.NodesViewModel

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
        )
    }
}
