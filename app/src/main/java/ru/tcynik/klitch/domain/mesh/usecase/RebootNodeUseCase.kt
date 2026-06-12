package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class RebootNodeUseCase(private val meshConfigRepository: MeshConfigRepository) {
    operator fun invoke() = meshConfigRepository.rebootNode()
}
