package ru.tcynik.klitch.data.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.gps.model.GpsLocation
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.klitch.mesh.common.util.nowMillis
import ru.tcynik.klitch.mesh.model.Position
import ru.tcynik.klitch.mesh.repository.CommandSender
import org.meshtastic.proto.Position as ProtoPosition

class OnConnectPositionSender(
    private val connectionRepository: MeshConnectionRepository,
    private val gpsRepository: GpsRepository,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val commandSender: CommandSender,
    private val logger: Logger,
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
        val cachedLocation = gpsRepository.location.value

        if (cachedLocation != null) {
            logger.d("GPS", "OnConnectPositionSender.sendPosition: GPS кеш доступен lat=${cachedLocation.latitude} lon=${cachedLocation.longitude}")
        } else {
            logger.d("GPS", "OnConnectPositionSender.sendPosition: GPS кеш пуст, ожидаю первый фикс")
        }

        val gpsLocation = cachedLocation
            ?: gpsRepository.location.filterNotNull().first()

        if (cachedLocation == null) {
            logger.d("GPS", "OnConnectPositionSender.sendPosition: GPS фикс получен lat=${gpsLocation.latitude} lon=${gpsLocation.longitude}")
        }

        val fixTimeSeconds = if (gpsLocation.time > 0) {
            (gpsLocation.time / 1_000L).toInt()
        } else {
            (nowMillis / 1_000L).toInt()
        }
        val protoPos = ProtoPosition(
            latitude_i = Position.degI(gpsLocation.latitude),
            longitude_i = Position.degI(gpsLocation.longitude),
            time = fixTimeSeconds,
            ground_speed = gpsLocation.speed?.toInt() ?: 0,
            ground_track = gpsLocation.bearing?.toInt() ?: 0,
            location_source = ProtoPosition.LocSource.LOC_EXTERNAL,
        )

        logger.d("GPS", "OnConnectPositionSender.sendPosition: отправляю позицию lat_i=${protoPos.latitude_i} lon_i=${protoPos.longitude_i} time=$fixTimeSeconds")

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