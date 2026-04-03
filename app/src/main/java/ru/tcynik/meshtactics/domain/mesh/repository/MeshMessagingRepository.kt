package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshMessageModel

interface MeshMessagingRepository {
    fun observeMessages(contactKey: String): Flow<List<MeshMessageModel>>
    suspend fun sendMessage(text: String, contactKey: String, channel: Int = 0)
}
