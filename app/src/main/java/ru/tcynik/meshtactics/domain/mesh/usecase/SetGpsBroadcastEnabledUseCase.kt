package ru.tcynik.meshtactics.domain.mesh.usecase

import ru.tcynik.meshtactics.domain.mesh.repository.GpsBroadcastSettingsRepository

class SetGpsBroadcastEnabledUseCase(
    private val repository: GpsBroadcastSettingsRepository,
) {
    suspend operator fun invoke(value: Boolean) = repository.set(value)
}
