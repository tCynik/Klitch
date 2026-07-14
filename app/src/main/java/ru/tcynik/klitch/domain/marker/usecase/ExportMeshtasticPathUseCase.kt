package ru.tcynik.klitch.domain.marker.usecase

import ru.tcynik.klitch.domain.marker.repository.MeshtasticPathFileRepository

class ExportMeshtasticPathUseCase(
    private val repository: MeshtasticPathFileRepository,
) {
    suspend operator fun invoke(markId: String, destinationUri: String): Result<Unit> =
        repository.export(markId, destinationUri)
}
