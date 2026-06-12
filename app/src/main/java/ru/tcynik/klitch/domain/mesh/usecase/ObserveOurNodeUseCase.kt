package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.MeshNodeModel
import ru.tcynik.klitch.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveOurNodeUseCase(
    private val repository: MeshNetworkRepository,
) : FlowUseCase<NoParams, MeshNodeModel?>() {
    override fun invoke(params: NoParams): Flow<MeshNodeModel?> =
        repository.observeOurNode()
}
