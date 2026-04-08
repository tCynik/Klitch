package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class DisconnectFromMeshUseCase(
    private val repository: MeshConnectionRepository,
) : UseCase<NoParams, Unit>() {
    override suspend fun invoke(params: NoParams) = repository.disconnect()
}
