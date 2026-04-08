package ru.tcynik.meshtactics.domain.map.usecase

import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.map.repository.LastMapPositionRepository

class SaveLastMapPositionUseCase(
    private val repository: LastMapPositionRepository,
) {
    operator fun invoke(position: MapCameraPosition) = repository.save(position)
}
