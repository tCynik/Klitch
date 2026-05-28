package ru.tcynik.meshtactics.domain.settings.usecase

import ru.tcynik.meshtactics.domain.settings.repository.NetworkSettingsRepository

class SetNetworkEnabledUseCase(
    private val repository: NetworkSettingsRepository,
) {
    operator fun invoke(enabled: Boolean) = repository.setNetworkEnabled(enabled)
}
