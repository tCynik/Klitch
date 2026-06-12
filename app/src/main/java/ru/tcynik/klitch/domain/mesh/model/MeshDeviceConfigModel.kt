package ru.tcynik.klitch.domain.mesh.model

data class MeshChannelModel(
    val index: Int,
    val name: String,
    val pskBase64: String,
)

data class MeshDeviceConfigModel(
    val longName: String,
    val shortName: String,
    val loraPreset: String,
    val txPowerDbm: String,
    val region: String,
    val channels: List<MeshChannelModel>,
)
