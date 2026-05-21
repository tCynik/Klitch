package ru.tcynik.meshtactics.domain.settings.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveGeoMarkSizeLevelUseCase(
    private val repository: MarkerSettingsRepository,
) : FlowUseCase<NoParams, Int>() {
    override fun invoke(params: NoParams): Flow<Int> = repository.geoMarkSizeLevelFlow
}
