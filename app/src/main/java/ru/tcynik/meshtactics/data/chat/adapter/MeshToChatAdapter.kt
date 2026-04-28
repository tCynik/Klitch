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
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageModel
import ru.tcynik.meshtactics.domain.chat.model.ContactType
import ru.tcynik.meshtactics.mesh.common.util.nowMillis
import ru.tcynik.meshtactics.mesh.model.DataPacket
import ru.tcynik.meshtactics.mesh.model.Message
import ru.tcynik.meshtactics.mesh.model.MessageStatus
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

    fun observeTotalUnreadCount(): Flow<Int> = packetRepository.getUnreadCountExcludingArchived()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeContactsAsFlow(): Flow<List<ChatContactDto>> =
        combine(
            packetRepository.getContacts(),
            packetRepository.getContactSettings(),
        ) { contacts, settings -> contacts to settings }
        .flatMapLatest { (contacts, settings) ->
            channelRepository.observeContours().flatMapLatest { contours ->
                val contourByHash = contours.associate { it.transport.meshtastic.channelHash to it.id }
                val contourNameById = contours.associate { it.id.value to it.name }

                val entries = contacts.entries.toList()
                if (entries.isEmpty()) return@flatMapLatest flowOf(emptyList())

                val unreadFlows = entries.map { (key, _) ->
                    packetRepository.getUnreadCountFlow(key)
                }
                combine(unreadFlows) { unreadCounts ->
                    val slotMaps = channelSlotResolver.mapsFlow.value
                    entries.mapIndexed { index, (contactKey, lastPacket) ->
                        val nodeId = contactKey.dropWhile { it.isDigit() }
                        val channelIndex = contactKey.firstOrNull()?.digitToIntOrNull() ?: -1
                        val isChannel = nodeId.startsWith("^")
                        val setting = settings[contactKey]

                        if (isChannel) {
                            val hash = slotMaps.slotToHash[channelIndex] ?: return@mapIndexed null
                            val contourId = contourByHash[hash] ?: return@mapIndexed null
                            val channelName = contourNameById[contourId.value] ?: nodeId
                            val isActive = contours.find { it.id == contourId }?.isActive ?: false
                            ChatContactDto(
                                id = contourId.value,
                                shortName = channelName,
                                longName = channelName,
                                type = ContactType.CHANNEL,
                                isFavorite = setting?.isFavorite ?: false,
                                isPinned = setting?.isPinned ?: false,
                                isArchived = setting?.isArchived ?: false,
                                isActive = isActive,
                                unreadCount = unreadCounts[index],
                                lastMessageTime = lastPacket.time.takeIf { it > 0 },
                                lastMessagePreview = lastPacket.text,
                            )
                        } else {
                            val node = nodeRepository.getNode(nodeId)
                            ChatContactDto(
                                id = contactKey,
                                shortName = node.user.short_name.ifBlank { nodeId },
                                longName = node.user.long_name.ifBlank { node.user.short_name }
                                    .ifBlank { nodeId },
                                type = ContactType.PRIVATE,
                                isFavorite = setting?.isFavorite ?: false,
                                isPinned = setting?.isPinned ?: false,
                                isArchived = setting?.isArchived ?: false,
                                isActive = true,
                                unreadCount = unreadCounts[index],
                                lastMessageTime = lastPacket.time.takeIf { it > 0 },
                                lastMessagePreview = lastPacket.text,
                            )
                        }
                    }.filterNotNull().sortedByDescending { it.lastMessageTime }
                }
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
            val resolvedChannel = parsedChannel ?: channel
            val dbContactKey = if (parsedChannel != null) contactId else "$resolvedChannel$contactId"
            doSend(text, dest, resolvedChannel, dbContactKey)
        } else {
            val contours = channelRepository.observeContours().first()
            val contour = contours.find { it.id.value == contactId } ?: return
            val hash = contour.transport.meshtastic.channelHash
            val channelIndex = channelSlotResolver.hashToSlot[hash] ?: return
            doSend(text, DataPacket.ID_BROADCAST, channelIndex, "$channelIndex${DataPacket.ID_BROADCAST}")
        }
    }

    private suspend fun doSend(text: String, dest: String?, channelIndex: Int, dbContactKey: String) {
        val packet = DataPacket(to = dest, channel = channelIndex, text = text)
            .apply { status = MessageStatus.QUEUED }
        commandSender.sendData(packet)
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
        return "${slot}${DataPacket.ID_BROADCAST}"
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
