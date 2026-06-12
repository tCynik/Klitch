package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.repository.MarkerSettingsRepository

class GetShowGeoMarkNamesUseCase(
    private val repository: MarkerSettingsRepository,
) {
    operator fun invoke(): Boolean = repository.getShowGeoMarkNames()
}
