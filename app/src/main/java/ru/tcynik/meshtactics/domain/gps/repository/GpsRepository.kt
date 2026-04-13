package ru.tcynik.meshtactics.domain.gps.repository

import kotlinx.coroutines.flow.StateFlow
import ru.tcynik.meshtactics.domain.gps.model.GpsLocation

interface GpsRepository {
    val location: StateFlow<GpsLocation?>
    val isReceivingUpdates: StateFlow<Boolean>
}
