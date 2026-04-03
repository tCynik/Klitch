package ru.tcynik.meshtactics.domain.mesh.model

data class MeshDeviceModel(
    val address: String,
    val name: String,
    val rssi: Int,
)
