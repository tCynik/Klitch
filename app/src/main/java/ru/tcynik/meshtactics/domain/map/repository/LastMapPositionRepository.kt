package ru.tcynik.meshtactics.domain.map.repository

import ru.tcynik.meshtactics.domain.map.model.MapCameraPosition

interface LastMapPositionRepository {
    fun get(): MapCameraPosition?
    fun save(position: MapCameraPosition)
}
