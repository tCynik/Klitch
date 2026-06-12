package ru.tcynik.klitch.domain.marker.usecase

import ru.tcynik.klitch.domain.marker.repository.GeoMarkRepository

class ExtendGeoMarkUseCase(
    private val repository: GeoMarkRepository,
) {
    suspend operator fun invoke(id: String) {
        val nowSeconds = System.currentTimeMillis() / 1_000
        repository.updateExpiresAt(id, nowSeconds + EXTEND_TTL_SECONDS)
    }

    companion object {
        const val EXTEND_TTL_SECONDS = 28_800L
    }
}
