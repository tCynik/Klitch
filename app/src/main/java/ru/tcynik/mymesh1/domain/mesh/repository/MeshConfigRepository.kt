package ru.tcynik.mymesh1.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceConfigModel

interface MeshConfigRepository {
    fun observeDeviceConfig(): Flow<MeshDeviceConfigModel?>
    fun requestDeviceConfig()
    fun writeOwner(longName: String, shortName: String)
    fun writeChannel(index: Int, name: String, pskBase64: String)
}
