package ru.tcynik.meshtactics.data.chat.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.data.chat.dto.ChatContactDto
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
) {

    // ==================== CONTACTS ====================

    fun observeContactsAsFlow(): Flow<List<ChatContactDto>> =
        combine(
            packetRepository.getContacts(),
            packetRepository.getContactSettings(),
        ) { contacts, settings ->
            contacts.map { (contactKey, lastPacket) ->
                val nodeId = contactKey.dropWhile { it.isDigit() }
                val node = nodeRepository.getNode(nodeId)
                val isChannel = nodeId.startsWith("^")
                val setting = settings[contactKey]
                ChatContactDto(
                    id = contactKey,
                    shortName = node.user.short_name.ifBlank { nodeId },
                    longName = node.user.long_name.ifBlank { node.user.short_name }.ifBlank { nodeId },
                    type = if (isChannel) ContactType.CHANNEL else ContactType.PRIVATE,
                    isFavorite = setting?.isFavorite ?: false,
                    isPinned = setting?.isPinned ?: false,
                    isArchived = setting?.isArchived ?: false,
                    unreadCount = 0, // Phase 6: подключить через getUnreadCountFlow
                    lastMessageTime = lastPacket.time.takeIf { it > 0 },
                    lastMessagePreview = lastPacket.text,
                )
            }.sortedByDescending { it.lastMessageTime }
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
        val filtered = if (query.isEmpty()) {
            messages
        } else {
            messages.filter {
                it.text.contains(query, ignoreCase = true) ||
                    it.senderCallsign.contains(query, ignoreCase = true)
            }
        }
        return filtered.sortedBy { it.sentAt }
    }

    // ==================== WRITE OPERATIONS ====================

    suspend fun sendMessage(text: String, contactId: String, channel: Int) {
        val parsedChannel = contactId[0].digitToIntOrNull()
        val dest = if (parsedChannel != null) contactId.substring(1) else contactId
        val resolvedChannel = parsedChannel ?: channel
        val dbContactKey = if (parsedChannel != null) contactId else "$resolvedChannel$contactId"

        val packet = DataPacket(
            to = dest,
            channel = resolvedChannel,
            text = text,
        ).apply { status = MessageStatus.QUEUED }

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
        packetRepository.clearUnreadCount(contactId, timestamp = nowMillis)
    }

    suspend fun clearHistory(contactId: String) {
        packetRepository.deleteContacts(listOf(contactId))
    }

    suspend fun toggleFavorite(contactId: String, isFavorite: Boolean) =
        packetRepository.setFavorite(contactId, isFavorite)

    suspend fun togglePinned(contactId: String, isPinned: Boolean) =
        packetRepository.setPinned(contactId, isPinned)

    suspend fun toggleArchived(contactId: String, isArchived: Boolean) =
        packetRepository.setArchived(contactId, isArchived)
}

// ==================== MAPPERS ====================

private fun Message.toChatMessageModel(contactKey: String): ChatMessageModel = ChatMessageModel(
    id = uuid.toString(),
    senderNodeId = node.user.id,
    senderCallsign = node.user.long_name.ifBlank { node.user.short_name }.ifBlank { node.user.id },
    text = text,
    sentAt = receivedTime,
    channelId = contactKey,
)
