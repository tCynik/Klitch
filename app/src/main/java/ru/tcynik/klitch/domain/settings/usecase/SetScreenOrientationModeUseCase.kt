package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.model.ScreenOrientationMode
import ru.tcynik.klitch.domain.settings.repository.ScreenOrientationRepository

class SetScreenOrientationModeUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(mode: ScreenOrientationMode) = repository.setOrientationMode(mode)
}
