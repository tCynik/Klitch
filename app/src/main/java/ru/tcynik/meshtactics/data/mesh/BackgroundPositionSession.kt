package ru.tcynik.meshtactics.data.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.tcynik.meshtactics.domain.channel.ChannelSlotResolver
import ru.tcynik.meshtactics.domain.channel.repository.ContourRepository
import ru.tcynik.meshtactics.domain.gps.repository.GpsLifecycleController
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.mesh.model.Position
import ru.tcynik.meshtactics.mesh.repository.CommandSender
import ru.tcynik.meshtactics.mesh.repository.GeoSendPolicy
import ru.tcynik.meshtactics.mesh.repository.MeshLocationManager
import ru.tcynik.meshtactics.mesh.repository.NodeRepository
import ru.tcynik.meshtactics.mesh.repository.UiPrefs
import org.meshtastic.proto.Position as ProtoPosition

class BackgroundPositionSession(
    private val nodeRepository: NodeRepository,
    private val locationManager: MeshLocationManager,
    private val commandSender: CommandSender,
    private val uiPrefs: UiPrefs,
    private val geoSendPolicy: GeoSendPolicy,
    private val contourRepository: ContourRepository,
    private val channelSlotResolver: ChannelSlotResolver,
    private val gpsLifecycleController: GpsLifecycleController,
    private val logger: Logger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private var locationRequestsJob: Job? = null

    init {
        scope.launch { observeLocationPolicy() }
    }

    private suspend fun observeLocationPolicy() {
        nodeRepository.myNodeInfo.collect { myNodeEntity ->
            locationRequestsJob?.cancel()
            if (myNodeEntity != null) {
                locationRequestsJob =
                    uiPrefs.shouldProvideNodeLocation(myNodeEntity.myNodeNum)
                        .combine(geoSendPolicy.observeAllowed()) { shouldProvide, geoAllowed ->
                            shouldProvide && geoAllowed
                        }
                        .onEach { allowed ->
                            if (allowed) {
                                logger.d("GPS", "BackgroundPositionSession: starting GPS bridge for node ${myNodeEntity.myNodeNum}")
                                commandSender.setFixedPosition(myNodeEntity.myNodeNum, Position(0.0, 0.0, 0))
                                gpsLifecycleController.start()
                                locationManager.start(scope) { pos -> sendToAllSlots(pos) }
                            } else {
                                logger.d("GPS", "BackgroundPositionSession: stopping GPS bridge")
                                locationManager.stop()
                            }
                        }
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
