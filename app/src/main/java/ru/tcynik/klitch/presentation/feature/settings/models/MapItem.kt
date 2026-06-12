package ru.tcynik.klitch.presentation.feature.settings.models

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MapItem(
    val id: String,
    val name: String,
    val createdAt: Long,
    val isSelected: Boolean,
)

fun MapItem.formatDate(): String {
    val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(createdAt), ZoneId.systemDefault())
    return dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
}
