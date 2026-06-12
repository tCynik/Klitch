package ru.tcynik.klitch.data.chat.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.klitch.data.chat.dto.toDomain
import ru.tcynik.klitch.domain.chat.model.ChatContact
import ru.tcynik.klitch.domain.chat.model.ChatMessageModel
import ru.tcynik.klitch.domain.chat.repository.ChatMessageRepository
import ru.tcynik.klitch.domain.chat.repository.ChatRepository

class ChatRepositoryImpl(
    private val adapter: MeshToChatAdapter,
    private val chatMessageRepository: ChatMessageRepository,
) : ChatRepository {

    override fun observeContacts(): Flow<List<ChatContact>> =
        adapter.observeContactsAsFlow().map { list -> list.map { it.toDomain() } }

    override fun observeTotalUnreadCount(): Flow<Int> = adapter.observeTotalUnreadCount()

    override fun observeMessages(contactIds: Set<String>, searchQuery: String): Flow<List<ChatMessageModel>> =
        chatMessageRepository.observeMessages(contactIds, searchQuery)

    override suspend fun sendMessage(text: String, contactId: String, channel: Int) =
        adapter.sendMessage(text, contactId, channel)

    override suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) =
        adapter.toggleFavorite(contactId, isFavorite)

    override suspend fun togglePinned(contactId: String, isPinned: Boolean) =
        adapter.togglePinned(contactId, isPinned)

    override suspend fun toggleArchived(contactId: String, isArchived: Boolean) =
        adapter.toggleArchived(contactId, isArchived)

    override suspend fun clearHistory(contactId: String) {
        adapter.clearHistory(contactId)
        chatMessageRepository.deleteByChannelId(contactId)
    }

    override suspend fun markAsRead(contactId: String) =
        adapter.markAsRead(contactId)
}
