package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveDeviceConfigUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, MeshDeviceConfigModel?>() {
    override fun invoke(params: NoParams): Flow<MeshDeviceConfigModel?> =
        repository.observeDeviceConfig()
}
