package ru.tcynik.meshtactics.data.mqtt.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageModel
import ru.tcynik.meshtactics.domain.transport.repository.MessageRepository

// Stub — MQTT transport is post-MVP. Not wired into DI until implemented.
class MqttMessageRepositoryImpl : MessageRepository {
    override fun observeMessages(channelId: String): Flow<List<ChatMessageModel>> = TODO("MQTT transport post-MVP")
    override suspend fun sendMessage(channelId: String, text: String) = TODO("MQTT transport post-MVP")
}
