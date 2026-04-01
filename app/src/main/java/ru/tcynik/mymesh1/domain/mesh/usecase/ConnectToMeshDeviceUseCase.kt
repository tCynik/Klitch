package ru.tcynik.mymesh1.domain.mesh.usecase

import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.domain.usecase.base.UseCase

class ConnectToMeshDeviceUseCase(
    private val repository: MeshConnectionRepository,
) : UseCase<String, Unit>() {
    override suspend fun invoke(params: String) = repository.connect(params)
}
