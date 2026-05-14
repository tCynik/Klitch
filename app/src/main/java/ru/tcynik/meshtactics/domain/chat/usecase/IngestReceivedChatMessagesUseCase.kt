package ru.tcynik.meshtactics.domain.chat.usecase

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.DefaultContour
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
        val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it }

        //Log.i(TAG, "DBG ingest: total messages=${messages.size} ids=${messages.map { it.id }}")
        messages.forEach { msg ->
            val contactKey = msg.channelId
            val nodeId = contactKey.dropWhile { it.isDigit() }
            val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: run {
                //Log.w(TAG, "DBG ingest: DROP no digit prefix contactKey=$contactKey")
                return@forEach
            }
            val isChannel = nodeId.startsWith("^")

            val logicalChannelId = if (isChannel) {
                val contour = when (channelIndex) {
                    0 -> {
                        val emergency = contours.find { it.id == DefaultContour.ID }
                        if (emergency == null) {
                            Log.w(TAG, "emergency contour not found, drop")
                            return@forEach
                        }
                        if (!emergency.isActive) return@forEach
                        emergency
                    }
                    else -> {
                        val hash = maps.slotToHash[channelIndex]
                        if (hash == null) {
                            Log.w(TAG, "unknown slot $channelIndex, drop")
                            return@forEach
                        }
                        val found = contourByHash[hash]
                        if (found == null) {
                            Log.w(TAG, "no contour for hash $hash, drop")
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

    companion object {
        private const val TAG = "IngestChatMessages"
    }
}
