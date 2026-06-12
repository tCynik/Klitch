package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class CheckOwnPkcHealthUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke(): Boolean = repository.isOwnPkcKeyBroken()
}
