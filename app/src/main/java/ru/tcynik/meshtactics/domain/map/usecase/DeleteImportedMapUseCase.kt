package ru.tcynik.meshtactics.domain.map.usecase

import ru.tcynik.meshtactics.domain.map.repository.ImportedMapRepository

class DeleteImportedMapUseCase(
    private val repository: ImportedMapRepository,
) {
    suspend operator fun invoke(id: String) {
        repository.delete(id)
    }
}
