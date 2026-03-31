package ru.tcynik.mymesh1.presentation.feature.meshtest.state

data class ConfigTabState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val deviceConfig: DeviceConfigUi? = null,
    val channelConfig: ChannelConfigUi? = null,
)

data class DeviceConfigUi(
    val longName: String,
    val shortName: String,
    val loraPreset: String,
    val txPowerDbm: String,
    val region: String,
)

data class ChannelConfigUi(
    val channelName: String,
    val modemPreset: String,
    val pskMasked: String,
)
