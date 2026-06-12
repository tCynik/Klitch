package ru.tcynik.klitch.domain.mesh.repository

import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel

interface LastConnectedDeviceRepository {
    fun get(): MeshDeviceModel?
    suspend fun save(device: MeshDeviceModel)
}
