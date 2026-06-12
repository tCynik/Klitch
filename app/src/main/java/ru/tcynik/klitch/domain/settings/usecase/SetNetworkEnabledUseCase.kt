package ru.tcynik.klitch.domain.settings.usecase

import ru.tcynik.klitch.domain.settings.repository.NetworkSettingsRepository

class SetNetworkEnabledUseCase(
    private val repository: NetworkSettingsRepository,
) {
    operator fun invoke(enabled: Boolean) = repository.setNetworkEnabled(enabled)
}
