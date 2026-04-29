package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.domain.settings.repository.MapCacheSettingsRepository

class SetTileCacheModeUseCase(
    private val repository: MapCacheSettingsRepository,
) {
    operator fun invoke(mode: TileCacheMode) = repository.setTileCacheMode(mode)
}
