package ru.tcynik.meshtactics.domain.mesh.repository

import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel

interface LastConnectedDeviceRepository {
    fun get(): MeshDeviceModel?
    suspend fun save(device: MeshDeviceModel)
}
