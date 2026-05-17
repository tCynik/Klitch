package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.LocationConfigModel
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class ObserveLocationConfigUseCase(
    private val repository: MeshConfigRepository,
) {
    operator fun invoke(nodeNum: Int): Flow<LocationConfigModel> =
        repository.observeLocationConfig(nodeNum)
}
