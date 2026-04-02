package ru.tcynik.mymesh1.domain.mesh.usecase

import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.domain.usecase.base.UseCase

data class ConnectToMeshDeviceParams(val address: String, val deviceName: String)

class ConnectToMeshDeviceUseCase(
    private val repository: MeshConnectionRepository,
) : UseCase<ConnectToMeshDeviceParams, Unit>() {
    override suspend fun invoke(params: ConnectToMeshDeviceParams) =
        repository.connect(params.address, params.deviceName)
}
