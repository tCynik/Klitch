package ru.tcynik.meshtactics.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.meshtactics.domain.channel.model.LogicalChannelId
import ru.tcynik.meshtactics.domain.channel.model.MeshtasticBinding
import ru.tcynik.meshtactics.domain.channel.repository.LogicalChannelRepository
import ru.tcynik.meshtactics.domain.chat.repository.ChatMessageRepository

class IngestReceivedChatMessagesUseCase(
    private val adapter: MeshToChatAdapter,
    private val channelRepository: LogicalChannelRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {
    fun observe(): Flow<Unit> = combine(
        adapter.observeMessagesAsFlow(emptySet(), ""),
        channelRepository.observeChannels(),
    ) { messages, channels ->
        val channelByIndex: Map<Int, LogicalChannelId> = channels
            .flatMap { ch ->
                ch.transports.filterIsInstance<MeshtasticBinding>()
                    .mapNotNull { b -> b.resolvedSlot?.let { slot -> slot to ch.id } }
            }.toMap()

        messages.forEach { msg ->
            val contactKey = msg.channelId
            val nodeId = contactKey.dropWhile { it.isDigit() }
            val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: return@forEach
            val isChannel = nodeId.startsWith("^")

            val logicalChannelId = if (isChannel) {
                channelByIndex[channelIndex]?.value ?: return@forEach
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
