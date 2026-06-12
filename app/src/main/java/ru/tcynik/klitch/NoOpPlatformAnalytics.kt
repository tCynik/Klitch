package ru.tcynik.klitch

import ru.tcynik.klitch.mesh.repository.DataPair
import ru.tcynik.klitch.mesh.repository.PlatformAnalytics

class NoOpPlatformAnalytics : PlatformAnalytics {
    override fun track(event: String, vararg properties: DataPair) = Unit
    override fun setDeviceAttributes(firmwareVersion: String, model: String) = Unit
    override val isPlatformServicesAvailable: Boolean = false
}
