package ru.tcynik.meshtactics.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.chat.repository.ChatMessageRepository

class IngestReceivedChatMessagesUseCase(
    private val adapter: MeshToChatAdapter,
    private val channelRepository: ContourRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) {
    fun observe(): Flow<Unit> = combine(
        adapter.observeMessagesAsFlow(emptySet(), ""),
        channelRepository.observeContours(),
        channelSlotResolver.mapsFlow,
    ) { messages, contours, maps ->
        val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it.id }

        messages.forEach { msg ->
            val contactKey = msg.channelId
            val nodeId = contactKey.dropWhile { it.isDigit() }
            val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: return@forEach
            val isChannel = nodeId.startsWith("^")

            val logicalChannelId = if (isChannel) {
                val hash = maps.slotToHash[channelIndex] ?: return@forEach
                contourByHash[hash]?.value ?: return@forEach
            } else {
                contactKey
            }

            chatMessageRepository.insertIfAbsent(
                id = msg.id,
                logicalChannelId = logicalChannelId,
                senderNodeId = msg.senderNodeId,
                senderCallsign = msg.senderCallsign,
                text = msg.text,
                sentAt = msg.sentAt / 1_000L,
                isSelf = msg.isFromMe,
            )
        }
    }.map { }
}
