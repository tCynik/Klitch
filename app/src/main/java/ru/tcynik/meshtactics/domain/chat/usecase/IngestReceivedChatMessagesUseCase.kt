package ru.tcynik.meshtactics.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.logger.Logger
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
    private val logger: Logger,
) {
    fun observe(): Flow<Unit> = combine(
        adapter.observeMessagesAsFlow(emptySet(), ""),
        channelRepository.observeContours(),
        channelRepository.observePrimaryContourId(),
        channelSlotResolver.mapsFlow,
    ) { messages, contours, primaryId, maps ->
        val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it }

        //Log.i(TAG, "DBG ingest: total messages=${messages.size} ids=${messages.map { it.id }}")
        messages.forEach { msg ->
            val contactKey = msg.channelId
            val nodeId = contactKey.dropWhile { it.isDigit() }
            val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: run {
                //logger.w("Chat","DBG ingest: DROP no digit prefix contactKey=$contactKey")
                return@forEach
            }
            val isChannel = nodeId.startsWith("^")

            val logicalChannelId = if (isChannel) {
                val contour = when (channelIndex) {
                    0 -> {
                        val primary = contours.find { it.id == primaryId }
                        if (primary == null) {
                            logger.w("Chat", "primary contour not found for slot 0, drop")
                            return@forEach
                        }
                        primary
                    }
                    else -> {
                        val hash = maps.slotToHash[channelIndex]
                        if (hash == null) {
                            logger.w("Chat","unknown slot $channelIndex, drop")
                            return@forEach
                        }
                        val found = contourByHash[hash]
                        if (found == null) {
                            logger.w("Chat","no contour for hash $hash, drop")
                            return@forEach
                        }
                        if (!found.isActive) return@forEach
                        found
                    }
                }
                contour.id.value
            } else {
                contactKey
            }

            //Log.i(TAG, "DBG ingest: INSERT id=${msg.id} logicalChannelId=$logicalChannelId from=${msg.senderNodeId} isSelf=${msg.isFromMe}")
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
