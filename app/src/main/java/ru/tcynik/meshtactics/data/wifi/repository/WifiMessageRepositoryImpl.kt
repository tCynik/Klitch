package ru.tcynik.meshtactics.data.wifi.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageModel
import ru.tcynik.meshtactics.domain.transport.repository.MessageRepository

// Stub — Wi-Fi transport is post-MVP. Not wired into DI until implemented.
class WifiMessageRepositoryImpl : MessageRepository {
    override fun observeMessages(channelId: String): Flow<List<ChatMessageModel>> = TODO("Wi-Fi transport post-MVP")
    override suspend fun sendMessage(channelId: String, text: String) = TODO("Wi-Fi transport post-MVP")
}
