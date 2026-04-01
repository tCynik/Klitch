package ru.tcynik.mymesh1.data.mesh.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.tcynik.mymesh1.data.mesh.mapper.toMeshMessageModel
import ru.tcynik.mymesh1.domain.mesh.model.MeshMessageModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshMessagingRepository
import ru.tcynik.mymesh1.mesh.model.DataPacket
import ru.tcynik.mymesh1.mesh.repository.CommandSender
import ru.tcynik.mymesh1.mesh.repository.NodeRepository
import ru.tcynik.mymesh1.mesh.repository.PacketRepository

class MeshMessagingRepositoryImpl(
    private val packetRepository: PacketRepository,
    private val commandSender: CommandSender,
    private val nodeRepository: NodeRepository,
) : MeshMessagingRepository {

    override fun observeMessages(contactKey: String): Flow<List<MeshMessageModel>> = flow {
        val messagesFlow = packetRepository.getMessagesFrom(
            contact = contactKey,
            limit = 50,
            includeFiltered = false,
            getNode = { userId -> nodeRepository.getNode(userId ?: "") },
        )
        emitAll(messagesFlow.map { messages ->
            messages.map { it.toMeshMessageModel() }
        })
    }

    override suspend fun sendMessage(text: String, contactKey: String, channel: Int) {
        commandSender.sendData(
            DataPacket(
                to = contactKey,
                channel = channel,
                text = text,
            )
        )
    }
}
