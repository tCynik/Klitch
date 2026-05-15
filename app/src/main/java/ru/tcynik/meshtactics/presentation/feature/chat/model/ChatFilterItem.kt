package ru.tcynik.meshtactics.presentation.feature.chat.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Тип чата в списке фильтров
 */
enum class ChatType {
    CHANNEL,      // Канал
    DIRECT_CHAT   // Личная беседа
}

/**
 * Элемент списка фильтров (канал, личная беседа)
 * Секция «Архив» — это отдельный тип элемента с дочерними элементами.
 */
data class ChatFilterItem(
    val id: String,
    val name: String,
    val type: ChatType,
    val isChecked: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isActive: Boolean = true,
    val unreadCount: Int = 0,
    val lastMessageTime: Long = 0L,
    val lastMessagePreview: String = "",
    val isArchiveSection: Boolean = false,  // Признак секции «Архив»
    val isExpanded: Boolean = false,         // Раскрыт ли архив
    val children: ImmutableList<ChatFilterItem> = persistentListOf(),
    val partnerHasPKC: Boolean = false,
)

/**
 * Вкладка экрана чатов
 */
enum class ChatTab {
    FILTER,
    CHAT
}
