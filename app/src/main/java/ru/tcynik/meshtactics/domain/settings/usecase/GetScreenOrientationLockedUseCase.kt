package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.repository.ScreenOrientationRepository

class GetScreenOrientationLockedUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(): Boolean = repository.getOrientationLocked()
}
