package ru.tcynik.klitch.data.mesh.mapper

import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.mesh.ble.toMeshtasticDisplayShortName
import ru.tcynik.klitch.mesh.model.ConnectionState
import ru.tcynik.klitch.mesh.model.Node

fun ConnectionState.toMeshConnectionStatus(
    ourNode: Node?,
    connectingDeviceName: String = "",
    bleRssi: Int = 0,
): MeshConnectionStatus = when (this) {
    ConnectionState.Disconnected -> MeshConnectionStatus.Disconnected
    ConnectionState.Connecting -> MeshConnectionStatus.Connecting(
        connectingDeviceName.toMeshtasticDisplayShortName(),
    )
    ConnectionState.Connected -> {
        val rawShortName = ourNode?.user?.short_name.orEmpty()
        val displayName = rawShortName
            .ifBlank { connectingDeviceName }
            .toMeshtasticDisplayShortName()
        MeshConnectionStatus.Connected(
            nodeId = ourNode?.user?.id.orEmpty(),
            shortName = displayName,
            deviceName = displayName,
            rssi = bleRssi,
            batteryLevel = ourNode?.deviceMetrics?.battery_level ?: 0,
        )
    }
    ConnectionState.DeviceSleep -> MeshConnectionStatus.DeviceSleep
}
