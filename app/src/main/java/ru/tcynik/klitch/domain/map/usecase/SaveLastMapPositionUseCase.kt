package ru.tcynik.klitch.domain.map.usecase

import ru.tcynik.klitch.domain.map.model.MapCameraPosition
import ru.tcynik.klitch.domain.map.repository.LastMapPositionRepository

class SaveLastMapPositionUseCase(
    private val repository: LastMapPositionRepository,
) {
    operator fun invoke(position: MapCameraPosition) = repository.save(position)
}
