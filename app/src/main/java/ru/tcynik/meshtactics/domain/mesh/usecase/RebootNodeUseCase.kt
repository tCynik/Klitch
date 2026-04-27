package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.MeshConfigRepository

class RebootNodeUseCase(private val meshConfigRepository: MeshConfigRepository) {
    operator fun invoke() = meshConfigRepository.rebootNode()
}
