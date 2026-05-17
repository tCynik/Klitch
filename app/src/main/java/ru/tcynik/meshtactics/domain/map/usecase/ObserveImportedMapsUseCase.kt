package ru.tcynik.meshtactics.domain.map.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.map.model.ImportedMapOverlay
import ru.tcynik.meshtactics.domain.map.repository.ImportedMapRepository

class ObserveImportedMapsUseCase(
    private val repository: ImportedMapRepository,
) {
    operator fun invoke(): Flow<List<ImportedMapOverlay>> = repository.observeAll()
}
