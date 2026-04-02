package ru.tcynik.mymesh1.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshPacketLogModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshPacketLogRepository
import ru.tcynik.mymesh1.domain.usecase.base.FlowUseCase
import ru.tcynik.mymesh1.domain.usecase.base.NoParams

class ObservePacketLogUseCase(
    private val repository: MeshPacketLogRepository,
) : FlowUseCase<NoParams, List<MeshPacketLogModel>>() {
    override fun invoke(params: NoParams): Flow<List<MeshPacketLogModel>> =
        repository.observePacketLog()
}
