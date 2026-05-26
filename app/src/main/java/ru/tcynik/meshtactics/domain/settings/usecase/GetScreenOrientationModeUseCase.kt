package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.domain.settings.repository.ScreenOrientationRepository

class GetScreenOrientationModeUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(): ScreenOrientationMode = repository.getOrientationMode()
}
