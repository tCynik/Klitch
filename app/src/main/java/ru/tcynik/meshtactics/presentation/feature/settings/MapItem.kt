package ru.tcynik.meshtactics.presentation.feature.settings

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class MapItem(
    val id: String,
    val name: String,
    val date: LocalDateTime,
    val isSelected: Boolean = false,
)

fun MapItem.formatDate(): String {
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    return date.format(formatter)
}
