package ru.tcynik.klitch.domain.map.usecase

import ru.tcynik.klitch.domain.map.repository.ImportedMapRepository

class ImportMapFileUseCase(
    private val repository: ImportedMapRepository,
) {
    suspend operator fun invoke(uri: String, name: String, createdAt: Long) {
        repository.import(uri = uri, name = name, createdAt = createdAt)
    }
}
