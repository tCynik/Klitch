package ru.tcynik.klitch.domain.map.usecase

import ru.tcynik.klitch.domain.map.repository.ImportedMapRepository

class ToggleImportedMapSelectionUseCase(
    private val repository: ImportedMapRepository,
) {
    suspend operator fun invoke(id: String, selected: Boolean) {
        repository.setSelected(id, selected)
    }
}
