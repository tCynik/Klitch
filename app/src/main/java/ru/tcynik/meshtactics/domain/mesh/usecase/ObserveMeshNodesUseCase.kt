package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshNodeModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveMeshNodesUseCase(
    private val repository: MeshNetworkRepository,
) : FlowUseCase<NoParams, List<MeshNodeModel>>() {
    override fun invoke(params: NoParams): Flow<List<MeshNodeModel>> =
        repository.observeNodes()
}
