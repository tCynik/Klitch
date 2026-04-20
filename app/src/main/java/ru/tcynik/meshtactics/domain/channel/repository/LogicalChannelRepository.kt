package ru.tcynik.meshtactics.domain.channel.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId

interface LogicalChannelRepository {
    fun observeChannels(): Flow<List<LogicalChannel>>
    suspend fun saveChannel(channel: LogicalChannel)
    suspend fun deleteChannel(id: LogicalChannelId)
}
