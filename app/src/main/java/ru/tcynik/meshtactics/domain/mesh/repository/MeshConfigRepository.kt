package ru.tcynik.meshtactics.domain.mesh.repository

import kotlinx.coroutines.flow.Flow
import ru.tcynik.meshtactics.domain.mesh.model.GpsMode
import ru.tcynik.meshtactics.domain.mesh.model.LocationConfigModel
import ru.tcynik.meshtactics.domain.mesh.model.MeshDeviceConfigModel

interface MeshConfigRepository {
    fun observeDeviceConfig(): Flow<MeshDeviceConfigModel?>
    fun requestDeviceConfig()
    fun writeOwner(longName: String, shortName: String)
    fun writeChannel(index: Int, name: String, pskBase64: String)

    fun observeLocationConfig(nodeNum: Int): Flow<LocationConfigModel>
    fun setProvideLocation(nodeNum: Int, provide: Boolean)
    fun writePositionConfig(
        destNum: Int,
        gpsMode: GpsMode,
        broadcastSecs: Int,
        smartEnabled: Boolean,
        smartMinDist: Int,
        flags: Int,
    )
    fun writeChannelPositionPrecision(destNum: Int, channelIndex: Int, precision: Int)
    fun removeFixedPosition(destNum: Int)
}
