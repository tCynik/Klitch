package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.domain.usecase.base.UseCase

class DisconnectFromMeshUseCase(
    private val repository: MeshConnectionRepository,
) : UseCase<NoParams, Unit>() {
    override suspend fun invoke(params: NoParams) = repository.disconnect()
}
