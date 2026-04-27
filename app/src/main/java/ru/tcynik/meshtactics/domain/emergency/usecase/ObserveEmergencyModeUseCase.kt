package ru.tcynik.meshtactics.domain.emergency.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository

class ObserveEmergencyModeUseCase(
    private val repository: ContourRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeEmergencyIsActive()
}
