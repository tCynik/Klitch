package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class CommitSettingsEditUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke() = repository.commitSettingsEdit()
}
