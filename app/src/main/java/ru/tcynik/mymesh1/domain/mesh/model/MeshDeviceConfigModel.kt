package ru.tcynik.mymesh1.domain.mesh.model

data class MeshDeviceConfigModel(
    val longName: String,
    val shortName: String,
    val loraPreset: String,
    val txPowerDbm: String,
    val region: String,
    val channelName: String,
    val pskMasked: String,
)
