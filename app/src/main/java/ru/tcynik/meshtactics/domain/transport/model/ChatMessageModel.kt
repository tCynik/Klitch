package ru.tcynik.meshtactics.domain.transport.model

data class ChatMessageModel(
    val id: String,
    val senderNodeId: String,
    val senderCallsign: String,
    val text: String,
    val sentAt: Long,
    val channelId: String,
)
