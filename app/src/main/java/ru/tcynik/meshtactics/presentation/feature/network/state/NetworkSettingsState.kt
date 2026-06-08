package ru.tcynik.meshtactics.presentation.feature.network.state

import ru.tcynik.meshtactics.presentation.feature.network.state.models.LocationConfigUi

data class NetworkSettingsState(
    val isLoading: Boolean = false,
    val deviceConfig: DeviceConfigUi? = null,
    val channels: List<ChannelConfigUi> = emptyList(),
    val locationConfig: LocationConfigUi? = null,
    val originalDeviceConfig: DeviceConfigUi? = null,
    val originalChannels: List<ChannelConfigUi> = emptyList(),
    val useWakeLock: Boolean = false,
) {
    val hasChanges: Boolean
        get() = deviceConfig != originalDeviceConfig || channels != originalChannels

    val shortNameError: String?
        get() = if (deviceConfig?.shortName?.isEmpty() == true) "Минимум 1 символ" else null
}

data class DeviceConfigUi(
    val longName: String,
    val shortName: String,
    val loraPreset: String,
    val txPowerDbm: String,
    val region: String,
)

data class ChannelConfigUi(
    val index: Int,
    val channelName: String,
    val pskBase64: String,
    val pskError: String? = null,
)
