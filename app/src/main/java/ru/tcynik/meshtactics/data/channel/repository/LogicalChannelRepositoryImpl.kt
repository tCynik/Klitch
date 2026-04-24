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
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelHash
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
            .map { rows -> rows.map { it.toDomain(queries) } }

    override suspend fun saveChannel(channel: LogicalChannel) {
        val binding = channel.transports.filterIsInstance<MeshtasticBinding>().firstOrNull()
        val hash = if (binding != null) {
            LogicalChannelHash.compute(channel.metadata.name, binding.psk).value
        } else null
        queries.upsert(
            id = channel.id.value,
            name = channel.metadata.name,
            meshtasticSlot = null,
            meshtasticPsk = binding?.psk,
            isAutoSync = if (channel.isAutoSync) 1L else 0L,
            channelHash = hash,
        )
    }

    override suspend fun deleteChannel(id: LogicalChannelId) {
        queries.deleteById(id.value)
    }

    override suspend fun findByChannelHash(hash: LogicalChannelHash): LogicalChannel? =
        queries.selectByChannelHash(hash.value)
            .executeAsOneOrNull()
            ?.toDomain(queries)
}

private fun Logical_channel.toDomain(queries: LogicalChannelQueries): LogicalChannel {
    val psk = meshtastic_psk ?: return LogicalChannel(
        id = LogicalChannelId(id),
        metadata = ChannelMetadata(name = name),
        transports = emptyList(),
        isAutoSync = is_auto_sync != 0L,
    )
    val hash = channel_hash?.let { LogicalChannelHash(it) }
        ?: LogicalChannelHash.compute(name, psk).also { computed ->
            queries.updateChannelHash(channelHash = computed.value, id = id)
        }
    return LogicalChannel(
        id = LogicalChannelId(id),
        metadata = ChannelMetadata(name = name),
        transports = listOf(MeshtasticBinding(psk = psk, channelHash = hash)),
        isAutoSync = is_auto_sync != 0L,
    )
}
