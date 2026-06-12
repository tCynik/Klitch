package ru.tcynik.klitch.domain.mesh.usecase

import ru.tcynik.klitch.domain.mesh.repository.MeshConfigRepository

class BeginSettingsEditUseCase(
    private val repository: MeshConfigRepository,
) {
    suspend operator fun invoke(): Boolean = repository.beginSettingsEdit()
}
