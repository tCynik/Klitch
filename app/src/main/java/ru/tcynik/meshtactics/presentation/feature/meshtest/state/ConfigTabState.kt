package ru.tcynik.meshtactics.presentation.feature.meshtest.state

data class ConfigTabState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val deviceConfig: DeviceConfigUi? = null,
    val channels: List<ChannelConfigUi> = emptyList(),
)

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
