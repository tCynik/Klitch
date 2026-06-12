package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.model.TileCacheMode
import ru.tcynik.klitch.domain.settings.repository.MapCacheSettingsRepository

class GetTileCacheModeUseCase(
    private val repository: MapCacheSettingsRepository,
) {
    operator fun invoke(): TileCacheMode = repository.getTileCacheMode()
}
