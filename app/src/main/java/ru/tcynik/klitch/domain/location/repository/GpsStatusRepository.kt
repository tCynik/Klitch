package ru.tcynik.klitch.domain.location.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.klitch.domain.location.model.GpsRawStatus

interface GpsStatusRepository {
    fun observeRaw(): Flow<GpsRawStatus>
}
