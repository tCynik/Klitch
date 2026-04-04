package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ScanMeshDevicesUseCase(
    private val repository: MeshConnectionRepository,
) : FlowUseCase<NoParams, List<MeshDeviceModel>>() {
    override fun invoke(params: NoParams): Flow<List<MeshDeviceModel>> =
        repository.scanDevices()
}
