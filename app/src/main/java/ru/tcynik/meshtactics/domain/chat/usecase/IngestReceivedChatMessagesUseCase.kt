package ru.tcynik.meshtactics.domain.chat.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.mesh.model.MeshContactKey
import ru.tcynik.meshtactics.domain.channel.model.ContourResolution
import ru.tcynik.meshtactics.domain.channel.model.DeliveryPolicy
import ru.tcynik.meshtactics.domain.channel.model.InboundPacketKind
import ru.tcynik.meshtactics.domain.channel.model.contourOrNull
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.channel.usecase.ApplyDeliveryPolicyUseCase
import ru.tcynik.meshtactics.domain.channel.usecase.ResolveContourFromSlotUseCase
import ru.tcynik.meshtactics.domain.chat.repository.ChatMessageRepository
import ru.tcynik.meshtactics.domain.logger.Logger

class IngestReceivedChatMessagesUseCase(
    private val adapter: MeshToChatAdapter,
    private val channelRepository: ContourRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val resolveContourFromSlot: ResolveContourFromSlotUseCase,
    private val applyDeliveryPolicy: ApplyDeliveryPolicyUseCase,
    private val logger: Logger,
) {
    fun observe(): Flow<Unit> = combine(
        adapter.observeMessagesAsFlow(emptySet(), ""),
        channelRepository.observeContours(),
        channelRepository.observePrimaryContourId(),
        channelSlotResolver.mapsFlow,
        channelRepository.observeSosMode(),
    ) { messages, contours, primaryId, maps, sosMode ->
        messages.forEach { msg ->
            val contactKey = msg.channelId
            val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: return@forEach
            val isChannel = MeshContactKey(contactKey).isBroadcast

            val logicalChannelId = if (isChannel) {
                val resolution = resolveContourFromSlot(
                    slot = channelIndex,
                    contours = contours,
                    maps = maps,
                    primaryContourId = primaryId,
                    sosMode = sosMode,
                )
                when (applyDeliveryPolicy(resolution, InboundPacketKind.TEXT_MESSAGE)) {
                    DeliveryPolicy.DELIVER,
                    DeliveryPolicy.SILENT_STORE,
                    -> resolution.contourOrNull()?.id?.value
                    DeliveryPolicy.DROP -> {
                        if (resolution is ContourResolution.Drop) {
                            logger.w("Chat", resolution.reason)
                        }
                        return@forEach
                    }
                }
            } else {
                contactKey
            } ?: return@forEach

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
