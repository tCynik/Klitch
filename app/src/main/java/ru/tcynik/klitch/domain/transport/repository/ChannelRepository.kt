package ru.tcynik.klitch.domain.transport.repository

import kotlinx.coroutines.flow.Flow

// Transport-agnostic channel config interface.
// Implemented by Meshtastic, MQTT, and Wi-Fi transports.
interface ChannelRepository {
    fun observeChannelName(): Flow<String>
    suspend fun setChannelName(name: String)
    suspend fun setChannelPsk(psk: ByteArray)
}
