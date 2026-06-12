package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceModel
import ru.tcynik.klitch.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ScanMeshDevicesUseCase(
    private val repository: MeshConnectionRepository,
) : FlowUseCase<NoParams, List<MeshDeviceModel>>() {
    override fun invoke(params: NoParams): Flow<List<MeshDeviceModel>> =
        repository.scanDevices()
}
