package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.repository.GpsBroadcastSettingsRepository

class ObserveGpsBroadcastEnabledUseCase(
    private val repository: GpsBroadcastSettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.enabled
}
