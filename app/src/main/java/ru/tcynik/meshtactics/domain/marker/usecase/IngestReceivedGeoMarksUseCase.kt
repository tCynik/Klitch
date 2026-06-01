package ru.tcynik.meshtactics.domain.marker.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.Contour
import ru.tcynik.meshtactics.domain.channel.model.ChannelSlotMaps
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

class IngestReceivedGeoMarksUseCase(
    private val packetRepository: PacketRepository,
    private val channelRepository: ContourRepository,
    private val geoMarkRepository: GeoMarkRepository,
    private val adapter: GeoMarkWaypointAdapter,
    private val channelSlotResolver: ChannelSlotResolver,
    private val logger: Logger,
) {
    fun observe(): Flow<Unit> = combine(
        packetRepository.getWaypoints(),
        channelRepository.observeContours(),
        channelSlotResolver.mapsFlow,
        channelRepository.observeSosMode(),
    ) { packets, contours, maps, sosMode ->
        IngestArgs(packets, contours, maps, sosMode)
    }.flatMapConcat { args ->
        flow {
            ingestPackets(args.packets, args.contours, args.maps, args.sosMode)
            emit(Unit)
        }
    }

    private suspend fun ingestPackets(
        packets: List<DataPacket>,
        contours: List<Contour>,
        maps: ChannelSlotMaps,
        sosMode: Boolean,
    ) {
        val primaryId = channelRepository.getPrimaryContourId()
        val activeIds = geoMarkRepository.getActiveMarkIds()
        val activeWaypointIds = geoMarkRepository.getActiveWaypointIds()
        val dismissedIds = geoMarkRepository.getDismissedMarkIds()
        val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it }
        val nowSeconds = System.currentTimeMillis() / 1_000

        packets.forEach { packet ->
            if (packet.from == DataPacket.ID_LOCAL) return@forEach

            val contour = when (packet.channel) {
                0 -> contours.find { it.id == primaryId }
                1 -> {
                    if (!sosMode) {
                        logger.d("Map", "slot 1 outside SOS, drop geo packet")
                        return@forEach
                    }
                    contours.find { it.isEmergency }
                }
                else -> {
                    val hash = maps.slotToHash[packet.channel]
                    if (hash == null) {
                        logger.w("Map", "unknown slot ${packet.channel}, drop")
                        return@forEach
                    }
                    val found = contourByHash[hash]
                    if (found == null) {
                        logger.w("Map", "no contour for hash $hash, drop")
                        return@forEach
                    }
                    found.takeIf { it.isActive }
                }
            } ?: return@forEach

            val model = adapter.decode(packet) ?: return@forEach
            if (model.id in activeIds || model.id in dismissedIds) return@forEach
            if (model.waypointId != 0) {
                if (model.waypointId in activeWaypointIds) return@forEach
                if ("wp-${model.waypointId}" in dismissedIds) return@forEach
            }

            val expiresAt = model.expiresAt
            if (expiresAt != null && expiresAt < nowSeconds) return@forEach

            geoMarkRepository.persistReceived(model, contour.id)
        }
    }

    private data class IngestArgs(
        val packets: List<DataPacket>,
        val contours: List<Contour>,
        val maps: ChannelSlotMaps,
        val sosMode: Boolean,
    )
}
