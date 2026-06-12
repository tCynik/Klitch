package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.repository.ScreenOrientationRepository

class SetScreenOrientationLockedUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(locked: Boolean) = repository.setOrientationLocked(locked)
}
