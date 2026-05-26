package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.domain.settings.repository.ScreenOrientationRepository

class SetScreenOrientationModeUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(mode: ScreenOrientationMode) = repository.setOrientationMode(mode)
}
