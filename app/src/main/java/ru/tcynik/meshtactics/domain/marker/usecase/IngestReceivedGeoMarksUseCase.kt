package ru.tcynik.meshtactics.domain.marker.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

class IngestReceivedGeoMarksUseCase(
    private val packetRepository: PacketRepository,
    private val channelRepository: ContourRepository,
    private val geoMarkRepository: GeoMarkRepository,
    private val adapter: GeoMarkWaypointAdapter,
    private val channelSlotResolver: ChannelSlotResolver,
) {
    fun observe(): Flow<Unit> = combine(
        packetRepository.getWaypoints(),
        channelRepository.observeContours(),
        channelSlotResolver.mapsFlow,
    ) { packets, contours, maps ->
        val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it.id }

        val nowSeconds = System.currentTimeMillis() / 1_000

        packets.forEach { packet ->
            val hash = maps.slotToHash[packet.channel] ?: return@forEach
            val contourId = contourByHash[hash] ?: return@forEach
            val model = adapter.decode(packet) ?: return@forEach
            val expiresAt = model.expiresAt
            if (expiresAt != null && expiresAt < nowSeconds) return@forEach
            geoMarkRepository.persistReceived(model, contourId)
        }
    }.map { }
}
