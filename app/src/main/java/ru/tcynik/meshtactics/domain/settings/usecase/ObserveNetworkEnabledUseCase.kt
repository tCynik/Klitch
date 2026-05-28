package ru.tcynik.meshtactics.domain.settings.usecase

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.settings.repository.NetworkSettingsRepository
import ru.tcynik.meshtactics.domain.usecase.base.FlowUseCase
import ru.tcynik.meshtactics.domain.usecase.base.NoParams

class ObserveNetworkEnabledUseCase(
    private val repository: NetworkSettingsRepository,
) : FlowUseCase<NoParams, Boolean>() {
    override fun invoke(params: NoParams): Flow<Boolean> = repository.networkEnabledFlow
}
