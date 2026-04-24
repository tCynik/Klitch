package ru.tcynik.meshtactics.domain.marker.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.marker.adapter.GeoMarkWaypointAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

class IngestReceivedGeoMarksUseCase(
    private val packetRepository: PacketRepository,
    private val channelRepository: LogicalChannelRepository,
    private val geoMarkRepository: GeoMarkRepository,
    private val adapter: GeoMarkWaypointAdapter,
    private val channelSlotResolver: ChannelSlotResolver,
) {
    fun observe(): Flow<Unit> = combine(
        packetRepository.getWaypoints(),
        channelRepository.observeChannels(),
        channelSlotResolver.mapsFlow,
    ) { packets, channels, maps ->
        val channelByHash = channels
            .flatMap { ch ->
                ch.transports.filterIsInstance<MeshtasticBinding>()
                    .map { b -> b.channelHash to ch.id }
            }.toMap()

        val nowSeconds = System.currentTimeMillis() / 1_000

        packets.forEach { packet ->
            val hash = maps.slotToHash[packet.channel] ?: return@forEach
            val logicalChannelId = channelByHash[hash] ?: return@forEach
            val model = adapter.decode(packet) ?: return@forEach
            val expiresAt = model.expiresAt
            if (expiresAt != null && expiresAt < nowSeconds) return@forEach
            geoMarkRepository.persistReceived(model, logicalChannelId)
        }
    }.map { }
}
