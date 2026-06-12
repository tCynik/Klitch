package ru.tcynik.klitch.data.local.map

import com.russhwolf.settings.Settings
import ru.tcynik.klitch.domain.map.model.MapCameraPosition
import ru.tcynik.klitch.domain.map.repository.LastMapPositionRepository

private const val KEY_LAT  = "last_map_lat"
private const val KEY_LON  = "last_map_lon"
private const val KEY_ZOOM = "last_map_zoom"

class LastMapPositionRepositoryImpl(
    private val settings: Settings,
) : LastMapPositionRepository {

    override fun get(): MapCameraPosition? {
        val lat  = settings.getDoubleOrNull(KEY_LAT)  ?: return null
        val lon  = settings.getDoubleOrNull(KEY_LON)  ?: return null
        val zoom = settings.getDoubleOrNull(KEY_ZOOM) ?: return null
        return MapCameraPosition(lat = lat, lon = lon, zoom = zoom)
    }

    override fun save(position: MapCameraPosition) {
        settings.putDouble(KEY_LAT,  position.lat)
        settings.putDouble(KEY_LON,  position.lon)
        settings.putDouble(KEY_ZOOM, position.zoom)
    }
}
