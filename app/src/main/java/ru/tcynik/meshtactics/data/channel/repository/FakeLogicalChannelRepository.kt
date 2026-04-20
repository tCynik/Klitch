package ru.tcynik.meshtactics.data.channel.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannel
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository

class FakeLogicalChannelRepository : LogicalChannelRepository {

    private val _channels = MutableStateFlow<List<LogicalChannel>>(emptyList())

    override fun observeChannels(): Flow<List<LogicalChannel>> = _channels.asStateFlow()

    override suspend fun saveChannel(channel: LogicalChannel) {
        _channels.update { current ->
            val existing = current.indexOfFirst { it.id == channel.id }
            if (existing >= 0) current.toMutableList().also { it[existing] = channel }
            else current + channel
        }
    }

    override suspend fun deleteChannel(id: LogicalChannelId) {
        _channels.update { current -> current.filter { it.id != id } }
    }
}
