package ru.tcynik.klitch.domain.marker.usecase

import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository

class DeleteGeoMarksUseCase(
    private val repository: GeoMarkRepository,
) {
    suspend operator fun invoke(ids: List<String>) {
        ids.forEach { id -> repository.deleteById(id) }
    }
}
