package ru.tcynik.meshtactics.data.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.mesh.common.util.nowMillis
import ru.tcynik.meshtactics.mesh.model.Position
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import org.meshtastic.proto.Position as ProtoPosition

class OnConnectPositionSender(
    private val connectionRepository: MeshConnectionRepository,
    private val gpsRepository: GpsRepository,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val commandSender: CommandSender,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            connectionRepository.connectionStatus
                .map { it is MeshConnectionStatus.Connected }
                .distinctUntilChanged()
                .filter { it }
                .collect { sendPosition() }
        }
    }

    private suspend fun sendPosition() {
        val gpsLocation = gpsRepository.location.value
            ?: gpsRepository.location.filterNotNull().first()

        val protoPos = ProtoPosition(
            latitude_i = Position.degI(gpsLocation.latitude),
            longitude_i = Position.degI(gpsLocation.longitude),
            time = (nowMillis / 1_000L).toInt(),
            ground_speed = gpsLocation.speed?.toInt() ?: 0,
            ground_track = gpsLocation.bearing?.toInt() ?: 0,
            location_source = ProtoPosition.LocSource.LOC_EXTERNAL,
        )

        // Primary channel (slot 0): standard path — also updates own node position in local DB
        commandSender.sendPosition(protoPos, null, false)

        // Active contours on slots 2+ (slot 1 = Emergency, excluded per requirement)
        val contours = contourRepository.observeContours().first()
        val maps = channelSlotResolver.mapsFlow.value
        contours
            .filter { it.isActive }
            .mapNotNull { contour -> maps.hashToSlot[contour.transport.meshtastic.channelHash] }
            .filter { slot -> slot > 1 }
            .distinct()
            .forEach { slot -> commandSender.broadcastPosition(protoPos, slot) }
    }
}
