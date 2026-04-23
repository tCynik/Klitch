package ru.tcynik.meshtactics.domain.chat.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageModel

interface ChatMessageRepository {
    fun observeMessages(channelIds: Set<String>, searchQuery: String): Flow<List<ChatMessageModel>>
    suspend fun insertIfAbsent(
        id: String,
        logicalChannelId: String,
        senderNodeId: String,
        senderCallsign: String,
        text: String,
        sentAt: Long,
        isSelf: Boolean,
    )
    suspend fun deleteByChannelId(logicalChannelId: String)
}
