package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveDeviceConfigUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, MeshDeviceConfigModel?>() {
    override fun invoke(params: NoParams): Flow<MeshDeviceConfigModel?> =
        repository.observeDeviceConfig()
}
