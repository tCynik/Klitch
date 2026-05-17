package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class CheckOwnPkcHealthUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke(): Boolean = repository.isOwnPkcKeyBroken()
}
