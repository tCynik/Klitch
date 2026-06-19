package ru.tcynik.klitch.data.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.tcynik.klitch.data.gps.NodeGpsPositionSource
import ru.tcynik.klitch.domain.channel.ChannelSlotResolver
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.gps.repository.GpsLifecycleController
import ru.tcynik.klitch.domain.gps.usecase.ObservePositionSourceModeUseCase
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.mesh.model.Position
import ru.tcynik.klitch.mesh.repository.CommandSender
import ru.tcynik.klitch.mesh.repository.GeoSendPolicy
import ru.tcynik.klitch.mesh.repository.MeshLocationManager
import ru.tcynik.klitch.mesh.repository.NodeRepository
import ru.tcynik.klitch.mesh.repository.UiPrefs
import org.meshtastic.proto.Position as ProtoPosition

class BackgroundPositionSession(
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val commandSender: CommandSender,
    private val uiPrefs: UiPrefs,
    private val geoSendPolicy: GeoSendPolicy,
    private val contourRepository: ContourRepository,
    private val syncStateRepository: ContourSyncStateRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val gpsLifecycleController: GpsLifecycleController,
    private val observePositionSourceMode: ObservePositionSourceModeUseCase,
    private val nodeGpsPositionSource: NodeGpsPositionSource,
    private val logger: Logger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var locationRequestsJob: Job? = null
    private var nodeGpsMonitorJob: Job? = null

    init {
        scope.launch { observeLocationPolicy() }
    }

    private suspend fun observeLocationPolicy() {
        nodeRepository.myNodeInfo.collect { myNodeEntity ->
            locationRequestsJob?.cancel()
            nodeGpsMonitorJob?.cancel()
            if (myNodeEntity != null) {
                val nodeNum = myNodeEntity.myNodeNum
                locationRequestsJob =
                    uiPrefs.shouldProvideNodeLocation(nodeNum)
                        .combine(geoSendPolicy.observeAllowed()) { shouldProvide, geoAllowed ->
                            shouldProvide && geoAllowed
                        }
                        .combine(syncStateRepository.syncRequired) { allowed, syncRequired ->
                            allowed && !syncRequired
                        }
                        .combine(observePositionSourceMode(NoParams)) { allowed, mode -> allowed to mode }
                        .onEach { (allowed, mode) ->
                            when {
                                allowed && mode == PositionSourceMode.PHONE_GPS -> {
                                    logger.d("GPS", "BackgroundPositionSession: starting GPS bridge for node $nodeNum")
                                    commandSender.setFixedPosition(nodeNum, Position(0.0, 0.0, 0))
                                    gpsLifecycleController.start()
                                    locationManager.start(scope) { pos -> sendToAllSlots(pos) }
                                }
                                allowed && mode == PositionSourceMode.NODE_GPS -> {
                                    logger.d("GPS", "BackgroundPositionSession: node $nodeNum self-reports GPS, phone bridge skipped")
                                    locationManager.stop()
                                }
                                else -> {
                                    logger.d("GPS", "BackgroundPositionSession: stopping GPS bridge")
                                    locationManager.stop()
                                }
                            }
                        }
                        .launchIn(scope)
                nodeGpsMonitorJob =
                    nodeGpsPositionSource.observePosition()
                        .onEach { logger.d("GPS", "BackgroundPositionSession: node-GPS fix lat=${it.latitude} lon=${it.longitude} time=${it.time}") }
                        .launchIn(scope)
            } else {
                locationManager.stop()
            }
        }
    }

    private fun sendToAllSlots(pos: ProtoPosition) {
        commandSender.sendPosition(pos)
        scope.launch {
            val contours = contourRepository.observeContours().first()
            val maps = channelSlotResolver.mapsFlow.value
            contours
                .filter { it.isActive }
                .mapNotNull { contour -> maps.hashToSlot[contour.transport.meshtastic.channelHash] }
                .filter { slot -> slot > 1 }
                .distinct()
                .forEach { slot -> commandSender.broadcastPosition(pos, slot) }
        }
    }
}
