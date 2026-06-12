package ru.tcynik.klitch.domain.map.usecase

import ru.tcynik.klitch.domain.map.repository.ImportedMapRepository

class HideImportedMapUseCase(
    private val repository: ImportedMapRepository,
) {
    suspend operator fun invoke(id: String) {
        repository.hide(id)
    }
}
