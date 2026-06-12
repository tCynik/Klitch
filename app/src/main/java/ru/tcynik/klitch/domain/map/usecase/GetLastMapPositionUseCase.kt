package ru.tcynik.klitch.domain.map.usecase

import ru.tcynik.klitch.domain.map.model.MapCameraPosition
import ru.tcynik.klitch.domain.map.repository.LastMapPositionRepository

class GetLastMapPositionUseCase(
    private val repository: LastMapPositionRepository,
) {
    operator fun invoke(): MapCameraPosition? = repository.get()
}
