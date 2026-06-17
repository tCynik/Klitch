package ru.tcynik.klitch.domain.service

import kotlinx.coroutines.flow.Flow

interface GpsServiceController {
    val shouldRunService: Flow<Boolean>
    fun onNodeConnected()
    fun onNetworkDisabled()
}
