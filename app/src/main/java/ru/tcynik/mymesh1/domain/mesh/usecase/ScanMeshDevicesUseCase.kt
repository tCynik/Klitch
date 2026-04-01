package ru.tcynik.mymesh1.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.mymesh1.domain.usecase.base.FlowUseCase
import ru.tcynik.mymesh1.domain.usecase.base.NoParams

class ScanMeshDevicesUseCase(
    private val repository: MeshConnectionRepository,
) : FlowUseCase<NoParams, List<MeshDeviceModel>>() {
    override fun invoke(params: NoParams): Flow<List<MeshDeviceModel>> =
        repository.scanDevices()
}
