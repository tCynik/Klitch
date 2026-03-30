package ru.tcynik.mymesh1.domain.model

import kotlinx.datetime.Instant

data class NodeModel(
    val id: String,
    val name: String,
    val address: String,    // MAC-адрес или IP
    val rssi: Int,          // уровень сигнала в dBm
    val isConnected: Boolean,
    val lastSeen: Instant,
)
