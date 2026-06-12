package ru.tcynik.meshtactics.data.chat.adapter

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.dto.ChatContactDto
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.model.isEmergency
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageModel
import ru.tcynik.meshtactics.domain.chat.model.ContactType
import ru.tcynik.meshtactics.mesh.common.util.nowMillis
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.model.MeshContactKey
import ru.tcynik.meshtactics.mesh.model.Message
import ru.tcynik.meshtactics.mesh.model.MessageStatus
import ru.tcynik.meshtactics.mesh.model.Node
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.PacketRepository

/**
 * Единственное место в chat-фиче, импортирующее mesh.model.*.
 * Конвертирует mesh-данные в domain/data модели чата.
 */
class MeshToChatAdapter(
    private val packetRepository: PacketRepository,
    private val nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val channelRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
) {

    // ==================== CONTACTS ====================

    fun observeTotalUnreadCount(): Flow<Int> = observeContactsAsFlow()
        .map { contacts -> contacts.filter { !it.isArchived }.sumOf { it.unreadCount } }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeContactsAsFlow(): Flow<List<ChatContactDto>> =
        combine(
            packetRepository.getContacts(),
            packetRepository.getContactSettings(),
            channelRepository.observeContours(),
            channelSlotResolver.mapsFlow,
        ) { contacts, settings, contours, slotMaps ->
            Quadruple(contacts, settings, contours, slotMaps)
        }.flatMapLatest { (contacts, settings, contours, slotMaps) ->
            combine(
                nodeRepository.nodeDBbyNum,
                nodeRepository.myId,
                nodeRepository.ourNodeInfo,
                channelRepository.observeSosMode(),
            ) { nodesByNum, myId, myNode, sosMode ->
                Quadruple(nodesByNum, myId, myNode, sosMode)
            }.flatMapLatest { (nodesByNum, myId, myNode, sosMode) ->
                val contourUnreadFlows = contours.map { contour ->
                    val slot = slotMaps.hashToSlot[contour.transport.meshtastic.channelHash]
                    val contactKey = slot?.let { MeshContactKey.broadcast(it).raw }
                    contactKey?.let { packetRepository.getUnreadCountFlow(it) } ?: flowOf(0)
                }
                val privateCandidates = buildPrivateCandidates(
                    contacts = contacts,
                    nodesByNum = nodesByNum,
                    myId = myId,
                    myHasPKC = myNode?.hasPKC ?: false,
                )
                val privateUnreadFlows = privateCandidates.map { candidate ->
                    candidate.contactKey?.let { packetRepository.getUnreadCountFlow(it) } ?: flowOf(0)
                }
                val unreadFlows = contourUnreadFlows + privateUnreadFlows
                if (unreadFlows.isEmpty()) return@flatMapLatest flowOf(emptyList())

                combine(unreadFlows) { unreadCounts ->
                    val contourItems = contours.mapIndexed { index, contour ->
                        val slot = slotMaps.hashToSlot[contour.transport.meshtastic.channelHash]
                        val contactKey = slot?.let { MeshContactKey.broadcast(it).raw }
                        val lastPacket = contactKey?.let { contacts[it] }
                        val setting = contactKey?.let { settings[it] }

                        ChatContactDto(
                            id = contour.id.value,
                            shortName = contour.name,
                            longName = contour.name,
                            type = ContactType.CHANNEL,
                            isFavorite = setting?.isFavorite ?: false,
                            isPinned = setting?.isPinned ?: false,
                            isArchived = setting?.isArchived ?: false,
                            isActive = contour.isActive,
                            unreadCount = if (contour.isEmergency && !sosMode) 0 else unreadCounts[index],
                            lastMessageTime = lastPacket?.time?.takeIf { it > 0 },
                            lastMessagePreview = lastPacket?.text,
                        )
                    }.sortedByDescending { it.lastMessageTime ?: 0L }

                    val privateBaseIndex = contourItems.size
                    val privateItems = privateCandidates.mapIndexed { index, candidate ->
                        val setting = candidate.contactKey?.let { settings[it] }
                        val lastPacket = candidate.contactKey?.let { contacts[it] }
                        candidate to ChatContactDto(
                            id = candidate.id,
                            shortName = candidate.shortName,
                            longName = candidate.longName,
                            type = ContactType.PRIVATE,
                            isFavorite = setting?.isFavorite ?: false,
                            isPinned = setting?.isPinned ?: false,
                            isArchived = setting?.isArchived ?: false,
                            isActive = true,
                            unreadCount = unreadCounts[privateBaseIndex + index],
                            lastMessageTime = lastPacket?.time?.takeIf { it > 0 },
                            lastMessagePreview = lastPacket?.text,
                            partnerHasPKC = candidate.partnerHasPKC,
                        )
                    }.sortedWith(
                        compareByDescending<Pair<PrivateNodeCandidate, ChatContactDto>> { it.first.isOnline }
                            .thenByDescending { it.second.lastMessageTime ?: 0L }
                            .thenBy { it.second.longName.lowercase() }
                    ).map { it.second }

                    contourItems + privateItems
                }
            }
        }

    // ==================== EMERGENCY MUTE SYNC ====================

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeEmergencyMuteSync(): Flow<Unit> = combine(
        channelRepository.observeSosMode(),
        channelRepository.observeContours(),
        channelSlotResolver.mapsFlow,
    ) { sosMode, contours, maps ->
        Triple(sosMode, contours, maps)
    }.flatMapLatest { (sosMode, contours, maps) ->
        flow {
            val emergencyHash = contours.find { it.isEmergency }?.transport?.meshtastic?.channelHash
            val emergencySlot = emergencyHash?.let { maps.hashToSlot[it] }
            if (emergencySlot != null) {
                val emergencyKey = MeshContactKey.broadcast(emergencySlot).raw
                val muteUntil = if (sosMode) 0L else Long.MAX_VALUE
                packetRepository.setMuteUntil(listOf(emergencyKey), muteUntil)
            }
            emit(Unit)
        }
    }

    // ==================== MESSAGES ====================

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeMessagesAsFlow(contactIds: Set<String>, searchQuery: String): Flow<List<ChatMessageModel>> {
        return if (contactIds.isEmpty()) {
            packetRepository.getContacts().flatMapLatest { contacts ->
                combineMessageFlows(contacts.keys.toList(), searchQuery)
            }
        } else {
            combineMessageFlows(contactIds.toList(), searchQuery)
        }
    }

    private fun combineMessageFlows(
        contactKeys: List<String>,
        searchQuery: String,
    ): Flow<List<ChatMessageModel>> {
        if (contactKeys.isEmpty()) return flowOf(emptyList())
        val flows = contactKeys.map { key -> messagesFlowFor(key) }
        return combine(flows) { arrays ->
            mergeAndFilter(arrays.flatMap { it }, searchQuery)
        }
    }

    private fun messagesFlowFor(contactKey: String): Flow<List<ChatMessageModel>> = flow {
        emitAll(
            packetRepository.getMessagesFrom(
                contact = contactKey,
                includeFiltered = false,
                getNode = { userId -> nodeRepository.getNode(userId ?: "") },
            ).map { messages ->
                messages.map { it.toChatMessageModel(contactKey) }
            }
        )
    }

    private fun mergeAndFilter(
        messages: List<ChatMessageModel>,
        searchQuery: String,
    ): List<ChatMessageModel> {
        val query = searchQuery.trim()
        val filtered = if (query.isEmpty()) messages
        else messages.filter {
            it.text.contains(query, ignoreCase = true) ||
                it.senderCallsign.contains(query, ignoreCase = true)
        }
        return filtered.sortedBy { it.sentAt }
    }

    // ==================== WRITE OPERATIONS ====================

    suspend fun sendMessage(text: String, contactId: String, channel: Int) {
        if (contactId.contains('!') || contactId.contains('^')) {
            val parsedChannel = contactId[0].digitToIntOrNull()
            val dest = if (parsedChannel != null) contactId.substring(1) else contactId
            val resolvedChannel = when {
                parsedChannel == DataPacket.PKC_CHANNEL_INDEX -> DataPacket.PKC_CHANNEL_INDEX
                parsedChannel != null -> parsedChannel
                else -> channel
            }
            val dbContactKey = "$resolvedChannel$dest"
            doSend(text, dest, resolvedChannel, dbContactKey)
        } else {
            val contours = channelRepository.observeContours().first()
            val contour = contours.find { it.id.value == contactId } ?: return
            val hash = contour.transport.meshtastic.channelHash
            val channelIndex = channelSlotResolver.hashToSlot[hash] ?: return
            doSend(text, DataPacket.ID_BROADCAST, channelIndex, MeshContactKey.broadcast(channelIndex).raw)
        }
    }

    private suspend fun doSend(text: String, dest: String?, channelIndex: Int, dbContactKey: String) {
        val packet = DataPacket(to = dest, channel = channelIndex, text = text)
            .apply { status = MessageStatus.QUEUED }
        //android.util.Log.i("ChatAdapter", "DBG doSend: to=$dest channel=$channelIndex contactKey=$dbContactKey")
        commandSender.sendData(packet)
        //android.util.Log.i("ChatAdapter", "DBG doSend: after sendData packetId=${packet.id} status=${packet.status}")
        val myNodeNum = nodeRepository.ourNodeInfo.value?.num ?: 0
        packetRepository.savePacket(
            myNodeNum = myNodeNum,
            contactKey = dbContactKey,
            packet = packet,
            receivedTime = nowMillis,
            read = true,
        )
    }

    suspend fun markAsRead(contactId: String) {
        val contactKey = resolveContactKey(contactId) ?: return
        packetRepository.clearUnreadCount(contactKey, timestamp = nowMillis)
    }

    suspend fun clearHistory(contactId: String) {
        val contactKey = resolveContactKey(contactId) ?: return
        packetRepository.deleteContacts(listOf(contactKey))
    }

    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) {
        val contactKey = resolveContactKey(contactId) ?: return
        packetRepository.setFavorite(contactKey, isFavorite)
    }

    suspend fun togglePinned(contactId: String, isPinned: Boolean) {
        val contactKey = resolveContactKey(contactId) ?: return
        packetRepository.setPinned(contactKey, isPinned)
    }

    suspend fun toggleArchived(contactId: String, isArchived: Boolean) {
        val contactKey = resolveContactKey(contactId) ?: return
        packetRepository.setArchived(contactKey, isArchived)
    }

    private suspend fun resolveContactKey(contactId: String): String? {
        if (contactId.contains('!') || contactId.contains('^')) return contactId
        val contours = channelRepository.observeContours().first()
        val contour = contours.find { it.id.value == contactId } ?: return null
        val hash = contour.transport.meshtastic.channelHash
        val slot = channelSlotResolver.hashToSlot[hash] ?: return null
        return MeshContactKey.broadcast(slot).raw
    }
}

// ==================== MAPPERS ====================

private fun Message.toChatMessageModel(contactKey: String): ChatMessageModel = ChatMessageModel(
    id = uuid.toString(),
    senderNodeId = node.user.id,
    senderCallsign = node.user.long_name.ifBlank { node.user.short_name }.ifBlank { node.user.id },
    text = text,
    sentAt = receivedTime,
    channelId = contactKey,
    isFromMe = fromLocal,
)

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private data class PrivateNodeCandidate(
    val id: String,
    val contactKey: String?,
    val shortName: String,
    val longName: String,
    val isOnline: Boolean,
    val partnerHasPKC: Boolean = false,
)

private fun buildPrivateCandidates(
    contacts: Map<String, DataPacket>,
    nodesByNum: Map<Int, Node>,
    myId: String?,
    myHasPKC: Boolean,
): List<PrivateNodeCandidate> {
    val historyPrivateEntries = contacts.entries
        .filter { (_, packet) -> packet.to != DataPacket.ID_BROADCAST }
        .mapNotNull { (contactKey, _) ->
            val nodeId = MeshContactKey(contactKey).nodeId
            if (!nodeId.startsWith("!")) return@mapNotNull null
            PrivateNodeCandidate(
                id = contactKey,
                contactKey = contactKey,
                shortName = "",
                longName = "",
                isOnline = false,
            )
        }
        // PKC history (channel=8) must win over non-PKC if both exist for same node
        .sortedBy { if (it.contactKey?.startsWith("${DataPacket.PKC_CHANNEL_INDEX}") == true) 1 else 0 }
        .associateBy { MeshContactKey(it.id).nodeId }

    val onlineNodes = nodesByNum.values
        .filter { node ->
            val nodeId = node.user.id
            nodeId.startsWith("!") && nodeId != myId && node.isOnline
        }
        .associateBy { it.user.id }

    val allNodeIds = (historyPrivateEntries.keys + onlineNodes.keys).toSet()
    return allNodeIds.mapNotNull { nodeId ->
        val history = historyPrivateEntries[nodeId]
        val node = onlineNodes[nodeId] ?: nodesByNum.values.firstOrNull { it.user.id == nodeId }
        val shortName = node?.user?.short_name?.ifBlank { nodeId } ?: nodeId
        val longName = node?.user?.long_name?.ifBlank { shortName } ?: shortName
        val fallbackContactKey = when {
            node?.hasPKC == true && myHasPKC -> MeshContactKey.direct(DataPacket.PKC_CHANNEL_INDEX, nodeId).raw
            else -> MeshContactKey.direct(0, nodeId).raw
        }
        val resolvedContactKey = history?.contactKey ?: fallbackContactKey
        PrivateNodeCandidate(
            id = resolvedContactKey,
            contactKey = resolvedContactKey,
            shortName = shortName,
            longName = longName,
            isOnline = node?.isOnline ?: false,
            partnerHasPKC = node?.hasPKC ?: false,
        )
    }
}
