package ru.tcynik.mymesh1.data.settings

import com.russhwolf.settings.Settings

class AppSettings(private val settings: Settings) {

    fun getDeviceId(): String? = settings.getStringOrNull(KEY_DEVICE_ID)

    fun setDeviceId(id: String) = settings.putString(KEY_DEVICE_ID, id)

    fun getLastSync(): Long = settings.getLong(KEY_LAST_SYNC, 0L)

    fun setLastSync(timestamp: Long) = settings.putLong(KEY_LAST_SYNC, timestamp)

    companion object {
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC  = "last_sync"
    }
}
