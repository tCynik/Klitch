package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveConnectionStatusUseCase(
    private val repository: MeshConnectionRepository,
) : FlowUseCase<NoParams, MeshConnectionStatus>() {
    override fun invoke(params: NoParams): Flow<MeshConnectionStatus> =
        repository.connectionStatus
}
