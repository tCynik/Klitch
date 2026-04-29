package ru.tcynik.meshtactics.domain.settings.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveTileCacheModeUseCase(
    private val repository: MapCacheSettingsRepository,
) : FlowUseCase<NoParams, TileCacheMode>() {
    override fun invoke(params: NoParams): Flow<TileCacheMode> = repository.tileCacheModeFlow
}
