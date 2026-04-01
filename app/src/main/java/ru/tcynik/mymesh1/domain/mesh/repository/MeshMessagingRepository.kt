package ru.tcynik.mymesh1.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshMessageModel

interface MeshMessagingRepository {
    fun observeMessages(contactKey: String): Flow<List<MeshMessageModel>>
    suspend fun sendMessage(text: String, contactKey: String, channel: Int = 0)
}
