package ru.tcynik.meshtactics.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository

class AppSettings(private val settings: Settings) : MarkerSettingsRepository, MapCacheSettingsRepository {

    private val _markerSizeLevel = MutableStateFlow(getMarkerSizeLevel())
    override val markerSizeLevelFlow: StateFlow<Int> = _markerSizeLevel.asStateFlow()

    private val _geoMarkSizeLevel = MutableStateFlow(getGeoMarkSizeLevel())
    override val geoMarkSizeLevelFlow: StateFlow<Int> = _geoMarkSizeLevel.asStateFlow()

    private val _showGeoMarkNames = MutableStateFlow(getShowGeoMarkNames())
    override val showGeoMarkNamesFlow: StateFlow<Boolean> = _showGeoMarkNames.asStateFlow()

    private val _tileCacheMode = MutableStateFlow(getTileCacheMode())
    override val tileCacheModeFlow: StateFlow<TileCacheMode> = _tileCacheMode.asStateFlow()

    fun getDeviceId(): String? = settings.getStringOrNull(KEY_DEVICE_ID)

    fun setDeviceId(id: String) = settings.putString(KEY_DEVICE_ID, id)

    fun getLastSync(): Long = settings.getLong(KEY_LAST_SYNC, 0L)

    fun setLastSync(timestamp: Long) = settings.putLong(KEY_LAST_SYNC, timestamp)

    override fun getMarkerSizeLevel(): Int = settings.getInt(KEY_MARKER_SIZE_LEVEL, DEFAULT_MARKER_SIZE_LEVEL)

    override fun setMarkerSizeLevel(level: Int) {
        settings.putInt(KEY_MARKER_SIZE_LEVEL, level)
        _markerSizeLevel.value = level
    }

    override fun getGeoMarkSizeLevel(): Int = settings.getInt(KEY_GEO_MARK_SIZE_LEVEL, DEFAULT_GEO_MARK_SIZE_LEVEL)

    override fun setGeoMarkSizeLevel(level: Int) {
        settings.putInt(KEY_GEO_MARK_SIZE_LEVEL, level)
        _geoMarkSizeLevel.value = level
    }

    override fun getShowGeoMarkNames(): Boolean = settings.getBoolean(KEY_SHOW_GEO_MARK_NAMES, false)

    override fun setShowGeoMarkNames(enabled: Boolean) {
        settings.putBoolean(KEY_SHOW_GEO_MARK_NAMES, enabled)
        _showGeoMarkNames.value = enabled
    }

    override fun getTileCacheMode(): TileCacheMode =
        settings.getStringOrNull(KEY_TILE_CACHE_MODE)
            ?.let { runCatching { TileCacheMode.valueOf(it) }.getOrNull() }
            ?: TileCacheMode.DEFAULT

    override fun setTileCacheMode(mode: TileCacheMode) {
        settings.putString(KEY_TILE_CACHE_MODE, mode.name)
        _tileCacheMode.value = mode
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_MARKER_SIZE_LEVEL = "marker_size_level"
        private const val KEY_GEO_MARK_SIZE_LEVEL = "geo_mark_size_level"
        private const val KEY_SHOW_GEO_MARK_NAMES = "show_geo_mark_names"
        private const val KEY_TILE_CACHE_MODE = "tile_cache_mode"
        private const val DEFAULT_MARKER_SIZE_LEVEL = 5
        private const val DEFAULT_GEO_MARK_SIZE_LEVEL = 5
    }
}
