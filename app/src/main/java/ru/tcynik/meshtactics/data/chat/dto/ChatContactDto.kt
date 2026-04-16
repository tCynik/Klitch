package ru.tcynik.meshtactics.data.chat.dto

import ru.tcynik.meshtactics.domain.chat.model.ChatContact
import ru.tcynik.meshtactics.domain.chat.model.ContactType

data class ChatContactDto(
    val id: String,
    val shortName: String,
    val longName: String,
    val type: ContactType,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageTime: Long? = null,
    val lastMessagePreview: String? = null,
)

fun ChatContactDto.toDomain(): ChatContact = ChatContact(
    id = id,
    displayName = longName.ifBlank { shortName }.ifBlank { id },
    type = type,
    isFavorite = isFavorite,
    isPinned = isPinned,
    isArchived = isArchived,
    unreadCount = unreadCount,
    lastMessageTime = lastMessageTime,
    lastMessagePreview = lastMessagePreview,
)
