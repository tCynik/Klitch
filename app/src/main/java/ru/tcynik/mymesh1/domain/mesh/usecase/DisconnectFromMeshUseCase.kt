package ru.tcynik.mymesh1.domain.mesh.usecase

import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.domain.usecase.base.NoParams
import ru.tcynik.mymesh1.domain.usecase.base.UseCase

class DisconnectFromMeshUseCase(
    private val repository: MeshConnectionRepository,
) : UseCase<NoParams, Unit>() {
    override suspend fun invoke(params: NoParams) = repository.disconnect()
}
