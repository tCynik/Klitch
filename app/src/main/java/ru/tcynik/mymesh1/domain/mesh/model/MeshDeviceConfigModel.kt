package ru.tcynik.mymesh1.domain.mesh.model

data class MeshChannelModel(
    val index: Int,
    val name: String,
    val pskMasked: String,
)

data class MeshDeviceConfigModel(
    val longName: String,
    val shortName: String,
    val loraPreset: String,
    val txPowerDbm: String,
    val region: String,
    val channels: List<MeshChannelModel>,
)
