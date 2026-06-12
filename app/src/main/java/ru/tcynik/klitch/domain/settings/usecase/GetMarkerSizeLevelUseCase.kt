package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.repository.MarkerSettingsRepository

class GetMarkerSizeLevelUseCase(
    private val repository: MarkerSettingsRepository,
) {
    operator fun invoke(): Int = repository.getMarkerSizeLevel()
}
