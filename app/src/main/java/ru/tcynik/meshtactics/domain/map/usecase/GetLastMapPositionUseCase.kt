package ru.tcynik.meshtactics.domain.map.usecase

import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition
import ru.tcynik.meshtactics.domain.map.repository.LastMapPositionRepository

class GetLastMapPositionUseCase(
    private val repository: LastMapPositionRepository,
) {
    operator fun invoke(): MapCameraPosition? = repository.get()
}
