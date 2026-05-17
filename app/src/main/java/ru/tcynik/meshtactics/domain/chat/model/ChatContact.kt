package ru.tcynik.meshtactics.domain.chat.model

data class ChatContact(
    val id: String,
    val displayName: String,
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
