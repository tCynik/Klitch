package ru.tcynik.klitch.domain.emergency.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.channel.repository.ContourRepository

class ObserveEmergencyModeUseCase(
    private val repository: ContourRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeSosMode()
}
