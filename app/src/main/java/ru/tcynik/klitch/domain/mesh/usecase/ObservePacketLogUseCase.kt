package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.MeshPacketLogModel
import ru.tcynik.klitch.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObservePacketLogUseCase(
    private val repository: MeshPacketLogRepository,
) : FlowUseCase<NoParams, List<MeshPacketLogModel>>() {
    override fun invoke(params: NoParams): Flow<List<MeshPacketLogModel>> =
        repository.observePacketLog()
}
