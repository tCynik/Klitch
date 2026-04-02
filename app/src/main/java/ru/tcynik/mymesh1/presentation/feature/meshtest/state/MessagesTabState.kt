package ru.tcynik.mymesh1.presentation.feature.meshtest.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class MessagesTabState(
    val messages: ImmutableList<MeshMessageUi> = persistentListOf(),
    val inputText: String = "",
    val isSending: Boolean = false,
    val selectedChannel: Int = 0,
)

data class MeshMessageUi(
    val id: String,
    val text: String,
    val fromNodeId: String,
    val toNodeId: String,
    val formattedTime: String,
    val direction: MessageDirection,
    val status: MessageStatus,
)

enum class MessageDirection { Outgoing, Incoming }

enum class MessageStatus { Pending, Sent, Acked, Failed }
