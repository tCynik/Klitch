package ru.tcynik.klitch.domain.map.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.map.model.ImportedMapOverlay
import ru.tcynik.klitch.domain.map.repository.ImportedMapRepository

class ObserveImportedMapsUseCase(
    private val repository: ImportedMapRepository,
) {
    operator fun invoke(): Flow<List<ImportedMapOverlay>> = repository.observeAll()
}
