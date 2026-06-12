package ru.tcynik.klitch.presentation.feature.settings.models

sealed interface NodeWriteEvent {
    data class Sent(val channelName: String) : NodeWriteEvent
    data object NotConnected : NodeWriteEvent
    data object NoFreeSlot : NodeWriteEvent
}
