package ru.tcynik.meshtactics.presentation.feature.chat.model

/**
 * Тип чата в списке фильтров
 */
enum class ChatType {
    CHANNEL,      // Канал
    DIRECT_CHAT,  // Личная беседа
    ARCHIVE       // Архив
}

/**
 * Элемент списка фильтров (канал, личная беседа, архив)
 */
data class ChatFilterItem(
    val id: String,
    val name: String,
    val type: ChatType,
    val isChecked: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessageTime: Long = 0L,
    val lastMessagePreview: String = "",
)

/**
 * Вкладка экрана чатов
 */
enum class ChatTab(val index: Int) {
    FILTER(0),
    CHAT(1)
}
