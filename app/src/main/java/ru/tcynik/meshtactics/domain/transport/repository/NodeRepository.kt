package ru.tcynik.meshtactics.domain.transport.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.transport.model.ChannelNodeModel

// Transport-agnostic node observation interface.
// Implemented by Meshtastic, MQTT, and Wi-Fi transports.
interface NodeRepository {
    fun observeNodes(): Flow<List<ChannelNodeModel>>
}
