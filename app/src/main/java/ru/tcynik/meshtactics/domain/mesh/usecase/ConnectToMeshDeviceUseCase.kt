package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.LastConnectedDeviceRepository
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

data class ConnectToMeshDeviceParams(val address: String, val deviceName: String)

class ConnectToMeshDeviceUseCase(
    private val repository: MeshConnectionRepository,
    private val lastConnectedDevice: LastConnectedDeviceRepository,
) : UseCase<ConnectToMeshDeviceParams, Unit>() {
    override suspend fun invoke(params: ConnectToMeshDeviceParams) {
        lastConnectedDevice.save(MeshDeviceModel(address = params.address, name = params.deviceName, rssi = 0))
        repository.connect(params.address, params.deviceName)
    }
}
