package ru.tcynik.meshtactics.presentation.feature.meshtest.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class TelemetryTabState(
    val isLoading: Boolean = false,
    val deviceMetrics: DeviceMetricsUi? = null,
    val meshNodes: ImmutableList<MeshNodeUi> = persistentListOf(),
)

data class DeviceMetricsUi(
    val batteryLevel: Int?,
    val voltage: String?,
    val channelUtilization: String?,
    val airUtilTx: String?,
    val uptimeFormatted: String?,
)

data class MeshNodeUi(
    val nodeId: String,
    val shortName: String,
    val longName: String,
    val snr: String,
    val lastHeardFormatted: String,
    val hopsAway: Int?,
)
