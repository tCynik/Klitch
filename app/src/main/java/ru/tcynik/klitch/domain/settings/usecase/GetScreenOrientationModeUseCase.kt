package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.model.ScreenOrientationMode
import ru.tcynik.klitch.domain.settings.repository.ScreenOrientationRepository

class GetScreenOrientationModeUseCase(
    private val repository: ScreenOrientationRepository,
) {
    operator fun invoke(): ScreenOrientationMode = repository.getOrientationMode()
}
