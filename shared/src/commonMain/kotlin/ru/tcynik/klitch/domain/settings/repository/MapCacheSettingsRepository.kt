package ru.tcynik.klitch.domain.settings.repository

import kotlinx.coroutines.flow.StateFlow
import ru.tcynik.klitch.domain.settings.model.TileCacheMode

interface MapCacheSettingsRepository {
    val tileCacheModeFlow: StateFlow<TileCacheMode>
    fun getTileCacheMode(): TileCacheMode
    fun setTileCacheMode(mode: TileCacheMode)
}
