package ru.tcynik.mymesh1.data.mesh.mapper

import ru.tcynik.mymesh1.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.mymesh1.mesh.model.ConnectionState
import ru.tcynik.mymesh1.mesh.model.Node

fun ConnectionState.toMeshConnectionStatus(
    ourNode: Node?,
    connectingDeviceName: String = "",
    bleRssi: Int = 0,
): MeshConnectionStatus = when (this) {
    ConnectionState.Disconnected -> MeshConnectionStatus.Disconnected
    ConnectionState.Connecting -> MeshConnectionStatus.Connecting(connectingDeviceName)
    ConnectionState.Connected -> MeshConnectionStatus.Connected(
        nodeId = ourNode?.user?.long_name?.ifBlank { ourNode.user.id } ?: ourNode?.user?.id ?: "",
        rssi = bleRssi,
        batteryLevel = ourNode?.deviceMetrics?.battery_level ?: 0,
    )
    ConnectionState.DeviceSleep -> MeshConnectionStatus.DeviceSleep
}
