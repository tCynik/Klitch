package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.NodeSecurityModel
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveNodeSecurityConfigUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, NodeSecurityModel?>() {
    override fun invoke(params: NoParams): Flow<NodeSecurityModel?> =
        repository.observeSecurityConfig()
}
