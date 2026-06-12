package ru.tcynik.klitch.domain.settings.repository

import kotlinx.coroutines.flow.StateFlow

interface MarkerSettingsRepository {
    val markerSizeLevelFlow: StateFlow<Int>
    fun getMarkerSizeLevel(): Int
    fun setMarkerSizeLevel(level: Int)

    val geoMarkSizeLevelFlow: StateFlow<Int>
    fun getGeoMarkSizeLevel(): Int
    fun setGeoMarkSizeLevel(level: Int)

    val showGeoMarkNamesFlow: StateFlow<Boolean>
    fun getShowGeoMarkNames(): Boolean
    fun setShowGeoMarkNames(enabled: Boolean)
}
