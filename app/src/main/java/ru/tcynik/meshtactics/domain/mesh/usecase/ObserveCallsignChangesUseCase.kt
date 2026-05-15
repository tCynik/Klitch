package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveCallsignChangesUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, Int>() {
    override fun invoke(params: NoParams): Flow<Int> =
        repository.observeCallsignChanges()
}
