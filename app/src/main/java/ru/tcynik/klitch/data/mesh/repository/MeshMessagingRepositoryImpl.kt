package ru.tcynik.klitch.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.logger.Logger
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.data.mesh.mapper.toMeshMessageModel
import ru.tcynik.klitch.domain.mesh.model.MeshMessageModel
import ru.tcynik.klitch.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.klitch.mesh.common.util.nowMillis
import ru.tcynik.klitch.mesh.model.DataPacket
import ru.tcynik.klitch.mesh.model.MessageStatus
import ru.tcynik.klitch.mesh.repository.CommandSender
import ru.tcynik.klitch.mesh.repository.NodeRepository
import ru.tcynik.klitch.mesh.repository.PacketRepository

class MeshMessagingRepositoryImpl(
    private val packetRepository: PacketRepository,
    private val commandSender: CommandSender,
    private val nodeRepository: NodeRepository,
    private val logger: Logger,
) : MeshMessagingRepository {

    override fun observeMessages(contactKey: String): Flow<List<MeshMessageModel>> = flow {
        logger.i("Chat", "observeMessages: contactKey=$contactKey")
        val messagesFlow = packetRepository.getMessagesFrom(
            contact = contactKey,
            limit = 50,
            includeFiltered = false,
            getNode = { userId -> nodeRepository.getNode(userId ?: "") },
        )
        emitAll(messagesFlow.map { messages ->
            logger.i("Chat", "observeMessages: flow emitted ${messages.size} messages for key=$contactKey")
            messages.map { it.toMeshMessageModel() }
        })
    }

    override suspend fun sendMessage(text: String, contactKey: String, channel: Int) {
        // contactKey may arrive in channel-prefixed format "0^all" (from ViewModel using the DB key)
        // or as raw node ID "^all". Parse consistently with the mesh layer convention.
        val parsedChannel = contactKey[0].digitToIntOrNull()
        val dest = if (parsedChannel != null) contactKey.substring(1) else contactKey
        val resolvedChannel = parsedChannel ?: channel
        // DB contact_key format: "${channel}${nodeId}" e.g. "0^all"
        val dbContactKey = if (parsedChannel != null) contactKey else "$resolvedChannel$contactKey"
        logger.i("Chat", "sendMessage: dest=$dest channel=$resolvedChannel dbKey=$dbContactKey")
        val packet = DataPacket(
            to = dest,
            channel = resolvedChannel,
            text = text,
        ).apply { status = MessageStatus.QUEUED }
        commandSender.sendData(packet)
        // Save to DB so the message appears in the chat immediately.
        val myNodeNum = nodeRepository.ourNodeInfo.value?.num ?: 0
        packetRepository.savePacket(
            myNodeNum = myNodeNum,
            contactKey = dbContactKey,
            packet = packet,
            receivedTime = nowMillis,
            read = true,
        )
    }
}
