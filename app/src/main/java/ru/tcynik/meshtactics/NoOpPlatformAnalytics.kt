package ru.tcynik.meshtactics

import ru.tcynik.meshtactics.mesh.repository.DataPair
import ru.tcynik.meshtactics.mesh.repository.PlatformAnalytics

class NoOpPlatformAnalytics : PlatformAnalytics {
    override fun track(event: String, vararg properties: DataPair) = Unit
    override fun setDeviceAttributes(firmwareVersion: String, model: String) = Unit
    override val isPlatformServicesAvailable: Boolean = false
}
