package ru.tcynik.klitch.domain.marker.usecase

import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository

class ToggleGeoMarkVisibilityUseCase(
    private val repository: GeoMarkRepository,
) {
    suspend operator fun invoke(id: String, visible: Boolean) =
        repository.toggleVisibility(id, visible)
}
