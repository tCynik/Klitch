package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class RegeneratePkcKeysUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke() = repository.regeneratePkcKeys()
}
