package ru.tcynik.meshtactics.data.chat.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.tcynik.meshtactics.data.local.Chat_message
import ru.tcynik.meshtactics.data.local.ChatMessageQueries
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageModel
import ru.tcynik.meshtactics.domain.chat.repository.ChatMessageRepository

class ChatMessageRepositoryImpl(
    private val queries: ChatMessageQueries,
) : ChatMessageRepository {

    override fun observeMessages(channelIds: Set<String>, searchQuery: String): Flow<List<ChatMessageModel>> =
        queries.selectAll()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                var result = if (channelIds.isEmpty()) {
                    rows.map { it.toChatMessageModel() }
                } else {
                    rows.filter { it.logical_channel_id in channelIds }.map { it.toChatMessageModel() }
                }
                if (searchQuery.isNotEmpty()) {
                    val q = searchQuery.trim()
                    result = result.filter {
                        it.text.contains(q, ignoreCase = true) ||
                            it.senderCallsign.contains(q, ignoreCase = true)
                    }
                }
                result
            }

    override suspend fun insertIfAbsent(
        id: String,
        logicalChannelId: String,
        senderNodeId: String,
        senderCallsign: String,
        text: String,
        sentAt: Long,
        isSelf: Boolean,
    ) = withContext(Dispatchers.IO) {
        queries.insertIfAbsent(
            id = id,
            logical_channel_id = logicalChannelId,
            sender_node_id = senderNodeId,
            sender_callsign = senderCallsign,
            text = text,
            sent_at = sentAt,
            is_self = if (isSelf) 1L else 0L,
        )
    }

    override suspend fun deleteByChannelId(logicalChannelId: String) = withContext(Dispatchers.IO) {
        queries.deleteByChannelId(logicalChannelId)
    }
}

private fun Chat_message.toChatMessageModel(): ChatMessageModel = ChatMessageModel(
    id = id,
    senderNodeId = sender_node_id,
    senderCallsign = sender_callsign.ifBlank { sender_node_id },
    text = text,
    sentAt = sent_at * 1_000L,
    channelId = logical_channel_id,
    isFromMe = is_self != 0L,
)
