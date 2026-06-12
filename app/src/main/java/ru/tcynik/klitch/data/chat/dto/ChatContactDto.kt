package ru.tcynik.klitch.data.chat.dto

import ru.tcynik.klitch.domain.chat.model.ChatContact
import ru.tcynik.klitch.domain.chat.model.ContactType

data class ChatContactDto(
    val id: String,
    val shortName: String,
    val longName: String,
    val type: ContactType,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isActive: Boolean = true,
    val unreadCount: Int = 0,
    val lastMessageTime: Long? = null,
    val lastMessagePreview: String? = null,
    val partnerHasPKC: Boolean = false,
)

fun ChatContactDto.toDomain(): ChatContact = ChatContact(
    id = id,
    displayName = longName.ifBlank { shortName }.ifBlank { id },
    type = type,
    isFavorite = isFavorite,
    isPinned = isPinned,
    isArchived = isArchived,
    isActive = isActive,
    unreadCount = unreadCount,
    lastMessageTime = lastMessageTime,
    lastMessagePreview = lastMessagePreview,
    partnerHasPKC = partnerHasPKC,
)
