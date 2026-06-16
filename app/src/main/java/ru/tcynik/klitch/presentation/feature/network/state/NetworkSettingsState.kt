package ru.tcynik.klitch.presentation.feature.network.state

import androidx.annotation.StringRes
import ru.tcynik.klitch.R
import ru.tcynik.klitch.presentation.feature.network.state.models.LocationConfigUi

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

    @get:StringRes
    val shortNameError: Int?
        get() = if (deviceConfig?.shortName?.isEmpty() == true) R.string.network_settings_short_name_empty else null
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
