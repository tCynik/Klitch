package ru.tcynik.klitch.data.gps

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import ru.tcynik.klitch.domain.gps.model.GpsLocation
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.gps.repository.PositionSource
import ru.tcynik.klitch.mesh.repository.NodeRepository

/** Reads position from the connected node's own GPS telemetry instead of the phone's GPS chip. */
class NodeGpsPositionSource(private val nodeRepository: NodeRepository) : PositionSource {
    override val mode: PositionSourceMode = PositionSourceMode.NODE_GPS

    override fun observePosition(): Flow<GpsLocation> = nodeRepository.ourNodeInfo
        .filterNotNull()
        .mapNotNull { node ->
            val pos = node.validPosition ?: return@mapNotNull null
            GpsLocation(
                latitude = node.latitude,
                longitude = node.longitude,
                bearing = pos.ground_track?.toFloat(),
                speed = pos.ground_speed?.toFloat(),
                accuracy = 0f,
                elapsedRealtimeNanos = System.nanoTime(),
                time = pos.time * 1000L,
            )
        }
}
