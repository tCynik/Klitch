package ru.tcynik.klitch.domain.transport.model

import ru.tcynik.klitch.domain.marker.model.GeoPoint

data class ChannelNodeModel(
    val nodeId: String,
    val callsign: String,
    val position: GeoPoint?,
    val batteryLevel: Int,
    val snr: Float,
    val lastHeardAt: Long,
)
