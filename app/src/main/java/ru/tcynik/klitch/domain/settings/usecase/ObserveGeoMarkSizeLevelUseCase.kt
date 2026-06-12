package ru.tcynik.klitch.domain.settings.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.settings.repository.MarkerSettingsRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveGeoMarkSizeLevelUseCase(
    private val repository: MarkerSettingsRepository,
) : FlowUseCase<NoParams, Int>() {
    override fun invoke(params: NoParams): Flow<Int> = repository.geoMarkSizeLevelFlow
}
