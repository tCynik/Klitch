package ru.tcynik.meshtactics.domain.settings.repository

import kotlinx.coroutines.flow.StateFlow

interface MarkerSettingsRepository {
    val markerSizeLevelFlow: StateFlow<Int>
    fun getMarkerSizeLevel(): Int
    fun setMarkerSizeLevel(level: Int)
}
