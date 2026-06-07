package ru.tcynik.meshtactics.data.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.gps.model.GpsLocation
import ru.tcynik.meshtactics.domain.gps.repository.GpsRepository
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
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
    private val meshNetworkRepository: MeshNetworkRepository,
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

        // Start slot discovery immediately without waiting for GPS fix
        scope.launch { requestPositionsForUnknownSlots(cachedLocation) }

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

    private suspend fun requestPositionsForUnknownSlots(gpsLocation: GpsLocation?) {
        val ourNodeId = meshNetworkRepository.observeOurNode().first()?.nodeId ?: return
        val nodes = meshNetworkRepository.observeNodes().first()
        val recentThreshold = (nowMillis / 1000L - RECENT_NODE_WINDOW_SECONDS).toInt()
        val unknownNodes = nodes.filter {
            it.receivedOnSlot == null && it.nodeId != ourNodeId && it.lastHeard >= recentThreshold
        }

        logger.d("GPS", "OnConnectPositionSender.requestPositionsForUnknownSlots: всего нод=${nodes.size} с slot=null и активных=${unknownNodes.size} GPS=${if (gpsLocation != null) "есть" else "нет"}")

        val position = if (gpsLocation != null) {
            Position(latitude = gpsLocation.latitude, longitude = gpsLocation.longitude, altitude = 0)
        } else {
            Position(latitude = 0.0, longitude = 0.0, altitude = 0)
        }
        unknownNodes.forEach { node ->
            delay(POSITION_REQUEST_INTERVAL_MS)
            logger.d("GPS", "OnConnectPositionSender.requestPositionsForUnknownSlots: запрос позиции у nodeId='${node.longName}_${node.shortName}' num=${node.num}")
            commandSender.requestPosition(node.num, position)
        }
    }

    companion object {
        private const val POSITION_REQUEST_INTERVAL_MS = 500L
        private const val RECENT_NODE_WINDOW_SECONDS = 30 * 60L
    }
}
