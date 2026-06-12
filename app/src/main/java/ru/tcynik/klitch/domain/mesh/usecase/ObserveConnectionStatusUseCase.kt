package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.klitch.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveConnectionStatusUseCase(
    private val repository: MeshConnectionRepository,
) : FlowUseCase<NoParams, MeshConnectionStatus>() {
    override fun invoke(params: NoParams): Flow<MeshConnectionStatus> =
        repository.connectionStatus
}
