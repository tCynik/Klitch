package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository

class GetShowGeoMarkNamesUseCase(
    private val repository: MarkerSettingsRepository,
) {
    operator fun invoke(): Boolean = repository.getShowGeoMarkNames()
}
