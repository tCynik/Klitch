package ru.tcynik.meshtactics.presentation.feature.network.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class NetworkConnectionState(
    val scannedDevices: ImmutableList<BleDeviceUi> = persistentListOf(),
    val isScanning: Boolean = false,
    val connectingAddress: String? = null,
)

data class BleDeviceUi(
    val address: String,
    val name: String,
    val rssi: Int,
)
