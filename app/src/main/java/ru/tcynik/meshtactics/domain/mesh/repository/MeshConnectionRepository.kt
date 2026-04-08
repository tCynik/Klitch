package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel

interface MeshConnectionRepository {
    val connectionStatus: Flow<MeshConnectionStatus>
    fun scanDevices(): Flow<List<MeshDeviceModel>>
    suspend fun connect(address: String, deviceName: String)
    suspend fun disconnect()
}
