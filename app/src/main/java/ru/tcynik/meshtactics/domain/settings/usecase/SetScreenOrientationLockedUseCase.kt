package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.repository.ScreenOrientationRepository

class SetScreenOrientationLockedUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(locked: Boolean) = repository.setOrientationLocked(locked)
}
