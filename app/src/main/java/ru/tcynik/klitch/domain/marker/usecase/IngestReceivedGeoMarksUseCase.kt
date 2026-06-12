package ru.tcynik.klitch.domain.marker.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import ru.tcynik.klitch.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.model.ChannelSlotMaps
import ru.tcynik.klitch.domain.channel.model.Contour
import ru.tcynik.klitch.domain.channel.model.ContourResolution
import ru.tcynik.klitch.domain.channel.model.DeliveryPolicy
import ru.tcynik.klitch.domain.channel.model.InboundPacketKind
import ru.tcynik.klitch.domain.channel.model.contourOrNull
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.usecase.ApplyDeliveryPolicyUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository
import ru.tcynik.klitch.mesh.model.DataPacket
import ru.tcynik.klitch.mesh.repository.PacketRepository

class IngestReceivedGeoMarksUseCase(
    private val packetRepository: PacketRepository,
    private val channelRepository: ContourRepository,
    private val geoMarkRepository: GeoMarkRepository,
    private val adapter: GeoMarkWaypointAdapter,
    private val channelSlotResolver: ChannelSlotResolver,
    private val resolveContourFromSlot: ResolveContourFromSlotUseCase,
    private val applyDeliveryPolicy: ApplyDeliveryPolicyUseCase,
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
        val nowSeconds = System.currentTimeMillis() / 1_000

        packets.forEach { packet ->
            if (packet.from == DataPacket.ID_LOCAL) return@forEach

            val resolution = resolveContourFromSlot(
                slot = packet.channel,
                contours = contours,
                maps = maps,
                primaryContourId = primaryId,
                sosMode = sosMode,
            )
            if (applyDeliveryPolicy(resolution, InboundPacketKind.WAYPOINT) != DeliveryPolicy.DELIVER) {
                if (resolution is ContourResolution.SilentStore) {
                    logger.d("Map", "slot ${packet.channel} outside SOS, drop geo packet")
                }
                return@forEach
            }
            val contour = resolution.contourOrNull() ?: return@forEach

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
