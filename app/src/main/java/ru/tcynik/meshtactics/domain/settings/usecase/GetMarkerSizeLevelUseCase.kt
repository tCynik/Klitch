package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository

class GetMarkerSizeLevelUseCase(
    private val repository: MarkerSettingsRepository,
) {
    operator fun invoke(): Int = repository.getMarkerSizeLevel()
}
