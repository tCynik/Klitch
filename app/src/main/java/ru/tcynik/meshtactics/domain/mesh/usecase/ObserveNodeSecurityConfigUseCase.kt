package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.NodeSecurityModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveNodeSecurityConfigUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, NodeSecurityModel?>() {
    override fun invoke(params: NoParams): Flow<NodeSecurityModel?> =
        repository.observeSecurityConfig()
}
