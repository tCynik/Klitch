package ru.tcynik.klitch.domain.settings.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.settings.model.TileCacheMode
import ru.tcynik.klitch.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveTileCacheModeUseCase(
    private val repository: MapCacheSettingsRepository,
) : FlowUseCase<NoParams, TileCacheMode>() {
    override fun invoke(params: NoParams): Flow<TileCacheMode> = repository.tileCacheModeFlow
}
