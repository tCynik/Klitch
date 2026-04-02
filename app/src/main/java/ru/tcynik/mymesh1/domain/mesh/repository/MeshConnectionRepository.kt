package ru.tcynik.mymesh1.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceModel

interface MeshConnectionRepository {
    val connectionStatus: Flow<MeshConnectionStatus>
    fun scanDevices(): Flow<List<MeshDeviceModel>>
    suspend fun connect(address: String, deviceName: String)
    suspend fun disconnect()
}
