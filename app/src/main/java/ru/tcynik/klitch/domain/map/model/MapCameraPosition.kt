package ru.tcynik.klitch.domain.map.model

data class MapCameraPosition(
    val lat: Double,
    val lon: Double,
    val zoom: Double,
    val bearing: Double = 0.0,
)
