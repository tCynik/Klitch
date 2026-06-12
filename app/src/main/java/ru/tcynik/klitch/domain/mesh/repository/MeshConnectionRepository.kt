package ru.tcynik.klitch.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel

interface MeshConnectionRepository {
    val connectionStatus: Flow<MeshConnectionStatus>
    fun scanDevices(): Flow<List<MeshDeviceModel>>
    /** Targeted scan for reconnect — does not compete with [scanDevices] collectors. */
    suspend fun findDeviceByAddress(address: String, timeoutMs: Long): MeshDeviceModel?
    suspend fun connect(address: String, deviceName: String)
    suspend fun disconnect()
}
