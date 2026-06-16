// Copyright (c) 2025 tCynik — modifications under GPL-3.0

package ru.tcynik.klitch.mesh.repository

import kotlinx.coroutines.flow.Flow

/** Controls whether the app is allowed to send position packets to the radio. */
interface GeoSendPolicy {
    /** Emits `true` when geo send is permitted, `false` when it must be blocked. */
    fun observeAllowed(): Flow<Boolean>
}
