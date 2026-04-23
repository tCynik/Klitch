package ru.tcynik.meshtactics.data.channel.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.local.Logical_channel
import ru.tcynik.meshtactics.data.local.LogicalChannelQueries
import ru.tcynik.meshtactics.domain.channel.model.ChannelMetadata
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository

class LogicalChannelRepositoryImpl(
    private val queries: LogicalChannelQueries,
) : LogicalChannelRepository {

    override fun observeChannels(): Flow<List<LogicalChannel>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveChannel(channel: LogicalChannel) {
        val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
        queries.upsert(
            id = channel.id.value,
            name = channel.metadata.name,
            meshtasticSlot = binding?.resolvedSlot?.toLong(),
            meshtasticPsk = binding?.psk,
            isAutoSync = if (channel.isAutoSync) 1L else 0L,
        )
    }

    override suspend fun deleteChannel(id: LogicalChannelId) {
        queries.deleteById(id.value)
    }
}

private fun Logical_channel.toDomain(): LogicalChannel {
    val psk = meshtastic_psk
    val binding = if (psk != null) {
        listOf(MeshtasticBinding(psk = psk, resolvedSlot = meshtastic_slot?.toInt()))
    } else emptyList()
    return LogicalChannel(
        id = LogicalChannelId(id),
        metadata = ChannelMetadata(name = name),
        transports = binding,
        isAutoSync = is_auto_sync != 0L,
    )
}
