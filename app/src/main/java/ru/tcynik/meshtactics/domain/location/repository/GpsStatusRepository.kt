package ru.tcynik.meshtactics.domain.location.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.location.model.GpsRawStatus

interface GpsStatusRepository {
    fun observeRaw(): Flow<GpsRawStatus>
}
