package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.mesh.model.LocationConfigModel
import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class ObserveLocationConfigUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(nodeNum: Int): Flow<LocationConfigModel> =
        repository.observeLocationConfig(nodeNum)
}
