package ru.tcynik.meshtactics.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import ru.tcynik.meshtactics.domain.settings.model.ScreenOrientationMode
import ru.tcynik.meshtactics.domain.settings.model.TileCacheMode
import ru.tcynik.meshtactics.domain.settings.repository.MapCacheSettingsRepository
import ru.tcynik.meshtactics.domain.settings.repository.MarkerSettingsRepository
import ru.tcynik.meshtactics.domain.settings.repository.NetworkSettingsRepository
import ru.tcynik.meshtactics.domain.settings.repository.ScreenOrientationRepository

class AppSettings(private val settings: Settings) :
    MarkerSettingsRepository,
    MapCacheSettingsRepository,
    ScreenOrientationRepository,
    NetworkSettingsRepository {

    private val _markerSizeLevel = MutableStateFlow(getMarkerSizeLevel())
    override val markerSizeLevelFlow: StateFlow<Int> = _markerSizeLevel.asStateFlow()

    private val _geoMarkSizeLevel = MutableStateFlow(getGeoMarkSizeLevel())
    override val geoMarkSizeLevelFlow: StateFlow<Int> = _geoMarkSizeLevel.asStateFlow()

    private val _showGeoMarkNames = MutableStateFlow(getShowGeoMarkNames())
    override val showGeoMarkNamesFlow: StateFlow<Boolean> = _showGeoMarkNames.asStateFlow()

    private val _tileCacheMode = MutableStateFlow(getTileCacheMode())
    override val tileCacheModeFlow: StateFlow<TileCacheMode> = _tileCacheMode.asStateFlow()

    private val _orientationLocked = MutableStateFlow(getOrientationLocked())
    private val _orientationMode = MutableStateFlow(getOrientationMode())

    private val _networkEnabled = MutableStateFlow(getNetworkEnabled())
    override val networkEnabledFlow: StateFlow<Boolean> = _networkEnabled.asStateFlow()

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

    // TODO: change default back to false when landscape orientation is implemented
    override fun getOrientationLocked(): Boolean =
        settings.getBoolean(KEY_SCREEN_ORIENTATION_LOCKED, true)

    override fun setOrientationLocked(locked: Boolean) {
        settings.putBoolean(KEY_SCREEN_ORIENTATION_LOCKED, locked)
        _orientationLocked.value = locked
    }

    override fun getOrientationMode(): ScreenOrientationMode =
        settings.getStringOrNull(KEY_SCREEN_ORIENTATION_MODE)
            ?.let { runCatching { ScreenOrientationMode.valueOf(it) }.getOrNull() }
            ?: ScreenOrientationMode.PORTRAIT

    override fun setOrientationMode(mode: ScreenOrientationMode) {
        settings.putString(KEY_SCREEN_ORIENTATION_MODE, mode.name)
        _orientationMode.value = mode
    }

    override fun observeOrientationSettings(): Flow<Pair<Boolean, ScreenOrientationMode>> =
        combine(_orientationLocked, _orientationMode) { locked, mode -> locked to mode }

    override fun getNetworkEnabled(): Boolean =
        settings.getBoolean(KEY_NETWORK_ENABLED, DEFAULT_NETWORK_ENABLED)

    override fun setNetworkEnabled(enabled: Boolean) {
        settings.putBoolean(KEY_NETWORK_ENABLED, enabled)
        _networkEnabled.value = enabled
    }

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val KEY_MARKER_SIZE_LEVEL = "marker_size_level"
        private const val KEY_GEO_MARK_SIZE_LEVEL = "geo_mark_size_level"
        private const val KEY_SHOW_GEO_MARK_NAMES = "show_geo_mark_names"
        private const val KEY_TILE_CACHE_MODE = "tile_cache_mode"
        private const val KEY_SCREEN_ORIENTATION_LOCKED = "screen_orientation_locked"
        private const val KEY_SCREEN_ORIENTATION_MODE = "screen_orientation_mode"
        private const val KEY_NETWORK_ENABLED = "network_enabled"
        private const val DEFAULT_MARKER_SIZE_LEVEL = 5
        private const val DEFAULT_GEO_MARK_SIZE_LEVEL = 5
        private const val DEFAULT_NETWORK_ENABLED = true
    }
}
