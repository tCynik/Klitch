package ru.tcynik.klitch.domain.map.repository

import ru.tcynik.klitch.domain.map.model.MapCameraPosition

interface LastMapPositionRepository {
    fun get(): MapCameraPosition?
    fun save(position: MapCameraPosition)
}
