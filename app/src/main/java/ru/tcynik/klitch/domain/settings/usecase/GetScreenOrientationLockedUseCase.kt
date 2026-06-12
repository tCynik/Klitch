package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.repository.ScreenOrientationRepository

class GetScreenOrientationLockedUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(): Boolean = repository.getOrientationLocked()
}
