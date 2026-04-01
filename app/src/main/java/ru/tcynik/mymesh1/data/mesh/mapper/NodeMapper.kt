package ru.tcynik.mymesh1.data.mesh.mapper

import ru.tcynik.mymesh1.domain.mesh.model.MeshNodeModel
import ru.tcynik.mymesh1.mesh.model.Node

fun Node.toMeshNodeModel(): MeshNodeModel = MeshNodeModel(
    num = num,
    nodeId = user.id,
    shortName = user.short_name.ifBlank { "??" },
    longName = user.long_name.ifBlank { "Unknown" },
    snr = if (snr == Float.MAX_VALUE) 0f else snr,
    rssi = if (rssi == Int.MAX_VALUE) 0 else rssi,
    lastHeard = lastHeard,
    hopsAway = hopsAway,
    batteryLevel = deviceMetrics.battery_level ?: 0,
    voltage = deviceMetrics.voltage ?: 0f,
    channelUtilization = deviceMetrics.channel_utilization ?: 0f,
    airUtilTx = deviceMetrics.air_util_tx ?: 0f,
    uptimeSeconds = (deviceMetrics.uptime_seconds ?: 0).toLong(),
)
