package ru.tcynik.klitch.domain.settings.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.settings.model.ScreenOrientationMode
import ru.tcynik.klitch.domain.settings.repository.ScreenOrientationRepository
import ru.tcynik.klitch.domain.usecase.base.FlowUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams

class ObserveScreenOrientationSettingsUseCase(
    private val repository: ScreenOrientationRepository,
) : FlowUseCase<NoParams, Pair<Boolean, ScreenOrientationMode>>() {
    override fun invoke(params: NoParams): Flow<Pair<Boolean, ScreenOrientationMode>> =
        repository.observeOrientationSettings()
}
