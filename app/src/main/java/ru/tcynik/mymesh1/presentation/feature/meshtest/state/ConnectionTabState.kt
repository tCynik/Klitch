package ru.tcynik.mymesh1.presentation.feature.meshtest.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ConnectionTabState(
    val scannedDevices: ImmutableList<BleDeviceUi> = persistentListOf(),
    val isScanning: Boolean = false,
)

data class BleDeviceUi(
    val address: String,
    val name: String,
    val rssi: Int,
)
