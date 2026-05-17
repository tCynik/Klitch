package ru.tcynik.meshtactics.domain.marker.usecase

import ru.tcynik.meshtactics.domain.marker.repository.GeoMarkRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

class DeleteExpiredGeoMarksUseCase(
    private val repository: GeoMarkRepository,
) : UseCase<NoParams, Unit>() {
    override suspend fun invoke(params: NoParams) {
        repository.deleteExpired(System.currentTimeMillis() / 1_000)
    }
}
