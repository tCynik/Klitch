package ru.tcynik.mymesh1.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.domain.usecase.base.FlowUseCase
import ru.tcynik.mymesh1.domain.usecase.base.NoParams

class ObserveConnectionStatusUseCase(
    private val repository: MeshConnectionRepository,
) : FlowUseCase<NoParams, MeshConnectionStatus>() {
    override fun invoke(params: NoParams): Flow<MeshConnectionStatus> =
        repository.connectionStatus
}
