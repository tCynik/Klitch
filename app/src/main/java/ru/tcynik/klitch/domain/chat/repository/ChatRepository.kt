package ru.tcynik.klitch.domain.chat.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.chat.model.ChatContact
import ru.tcynik.klitch.domain.chat.model.ChatMessageModel

interface ChatRepository {
    fun observeContacts(): Flow<List<ChatContact>>
    fun observeTotalUnreadCount(): Flow<Int>
    fun observeMessages(contactIds: Set<String>, searchQuery: String = ""): Flow<List<ChatMessageModel>>
    suspend fun sendMessage(text: String, contactId: String, channel: Int)
    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean)
    suspend fun togglePinned(contactId: String, isPinned: Boolean)
    suspend fun toggleArchived(contactId: String, isArchived: Boolean)
    suspend fun clearHistory(contactId: String)
    suspend fun markAsRead(contactId: String)
}
