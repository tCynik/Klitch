package ru.tcynik.mymesh1.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshNodeModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshNetworkRepository
import ru.tcynik.mymesh1.domain.usecase.base.FlowUseCase
import ru.tcynik.mymesh1.domain.usecase.base.NoParams

class ObserveOurNodeUseCase(
    private val repository: MeshNetworkRepository,
) : FlowUseCase<NoParams, MeshNodeModel?>() {
    override fun invoke(params: NoParams): Flow<MeshNodeModel?> =
        repository.observeOurNode()
}
