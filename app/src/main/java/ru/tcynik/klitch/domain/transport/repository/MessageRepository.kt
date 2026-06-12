package ru.tcynik.klitch.domain.transport.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.chat.model.ChatMessageModel

// Transport-agnostic messaging interface.
// Implemented by Meshtastic, MQTT, and Wi-Fi transports.
interface MessageRepository {
    fun observeMessages(channelId: String): Flow<List<ChatMessageModel>>
    suspend fun sendMessage(channelId: String, text: String)
}
