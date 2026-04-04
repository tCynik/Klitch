package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshPacketLogModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObservePacketLogUseCase(
    private val repository: MeshPacketLogRepository,
) : FlowUseCase<NoParams, List<MeshPacketLogModel>>() {
    override fun invoke(params: NoParams): Flow<List<MeshPacketLogModel>> =
        repository.observePacketLog()
}
