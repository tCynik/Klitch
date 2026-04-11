package ru.tcynik.meshtactics.data.settings

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(private val settings: Settings) {

    private val _markerSizeLevel = MutableStateFlow(getMarkerSizeLevelInternal())
    val markerSizeLevelFlow: StateFlow<Int> = _markerSizeLevel.asStateFlow()

    fun getDeviceId(): String? = settings.getStringOrNull(KEY_DEVICE_ID)

    fun setDeviceId(id: String) = settings.putString(KEY_DEVICE_ID, id)

    fun getLastSync(): Long = settings.getLong(KEY_LAST_SYNC, 0L)

    fun setLastSync(timestamp: Long) = settings.putLong(KEY_LAST_SYNC, timestamp)

    fun getMarkerSizeLevel(): Int = settings.getInt(KEY_MARKER_SIZE_LEVEL, DEFAULT_MARKER_SIZE_LEVEL)

    fun setMarkerSizeLevel(level: Int) {
        settings.putInt(KEY_MARKER_SIZE_LEVEL, level)
        _markerSizeLevel.value = level
    }

    private fun getMarkerSizeLevelInternal(): Int =
        settings.getInt(KEY_MARKER_SIZE_LEVEL, DEFAULT_MARKER_SIZE_LEVEL)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC  = "last_sync"
        private const val KEY_MARKER_SIZE_LEVEL = "marker_size_level"
        private const val DEFAULT_MARKER_SIZE_LEVEL = 5
    }
}
