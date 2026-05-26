package ru.tcynik.meshtactics.domain.settings.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.domain.settings.repository.ScreenOrientationRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveScreenOrientationSettingsUseCase(
    private val repository: ScreenOrientationRepository,
) : FlowUseCase<NoParams, Pair<Boolean, ScreenOrientationMode>>() {
    override fun invoke(params: NoParams): Flow<Pair<Boolean, ScreenOrientationMode>> =
        repository.observeOrientationSettings()
}
