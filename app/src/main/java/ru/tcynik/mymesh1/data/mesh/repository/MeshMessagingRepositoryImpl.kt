package ru.tcynik.mymesh1.data.mesh.repository

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.tcynik.mymesh1.data.mesh.mapper.toMeshMessageModel
import ru.tcynik.mymesh1.domain.mesh.model.MeshMessageModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.mymesh1.mesh.common.util.nowMillis
import ru.tcynik.mymesh1.mesh.model.DataPacket
import ru.tcynik.mymesh1.mesh.model.MessageStatus
import ru.tcynik.mymesh1.mesh.repository.CommandSender
import ru.tcynik.mymesh1.mesh.repository.NodeRepository
import ru.tcynik.mymesh1.mesh.repository.PacketRepository

class MeshMessagingRepositoryImpl(
    private val packetRepository: PacketRepository,
    private val commandSender: CommandSender,
    private val nodeRepository: NodeRepository,
) : MeshMessagingRepository {

    override fun observeMessages(contactKey: String): Flow<List<MeshMessageModel>> = flow {
        Log.i("MeshMessagingRepo", "DBG observeMessages: contactKey=$contactKey")
        val messagesFlow = packetRepository.getMessagesFrom(
            contact = contactKey,
            limit = 50,
            includeFiltered = false,
            getNode = { userId -> nodeRepository.getNode(userId ?: "") },
        )
        emitAll(messagesFlow.map { messages ->
            Log.i("MeshMessagingRepo", "DBG Room flow emitted: ${messages.size} messages for key=$contactKey")
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
        Log.i("MeshMessagingRepo", "DBG sendMessage: dest=$dest channel=$resolvedChannel dbKey=$dbContactKey")
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
