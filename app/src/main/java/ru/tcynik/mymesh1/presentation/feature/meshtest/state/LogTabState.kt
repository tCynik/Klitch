package ru.tcynik.mymesh1.presentation.feature.meshtest.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class LogTabState(
    val entries: ImmutableList<LogEntryUi> = persistentListOf(),
    val isPaused: Boolean = false,
    val activeFilter: LogFilter = LogFilter.All,
)

data class LogEntryUi(
    val formattedTime: String,
    val direction: LogDirection,
    val packetType: String,
    val summary: String,
    val rawHex: String?,
)

enum class LogFilter(val label: String) {
    All("All"),
    Incoming("In"),
    Outgoing("Out"),
    System("System"),
    Errors("Errors"),
}

enum class LogDirection { In, Out, System }
