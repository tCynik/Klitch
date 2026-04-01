package ru.tcynik.mymesh1.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.mymesh1.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.mymesh1.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.mymesh1.domain.usecase.base.FlowUseCase
import ru.tcynik.mymesh1.domain.usecase.base.NoParams

class ObserveDeviceConfigUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, MeshDeviceConfigModel?>() {
    override fun invoke(params: NoParams): Flow<MeshDeviceConfigModel?> =
        repository.observeDeviceConfig()
}
