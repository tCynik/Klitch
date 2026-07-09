package ru.tcynik.klitch.data.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.usecase.ObserveLocationConfigUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WriteChannelPositionPrecisionUseCase
import ru.tcynik.klitch.domain.mesh.usecase.WritePositionConfigUseCase
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.mesh.model.Position
import ru.tcynik.klitch.mesh.repository.CommandSender
import ru.tcynik.klitch.mesh.repository.GeoSendPolicy
import ru.tcynik.klitch.mesh.repository.MeshLocationManager
import ru.tcynik.klitch.mesh.repository.NodeRepository
import ru.tcynik.klitch.mesh.repository.UiPrefs
import ru.tcynik.klitch.mesh.service.PositionTrackingPolicy
import org.meshtastic.proto.Position as ProtoPosition

// NODE_GPS preset — node's own GPS chip is the position source, mirrors the PHONE_GPS preset
// in NodeProvisioningUseCase (HEADING|SPEED|ALTITUDE|TIMESTAMP flags, full channel precision).
private const val NODE_GPS_BROADCAST_SECS = PositionTrackingPolicy.STATIONARY_INTERVAL_SECS
private const val NODE_GPS_POSITION_FLAGS = 897
private const val NODE_GPS_CHANNEL_PRECISION = 32
private const val NODE_GPS_SMART_MIN_DIST_M = 20
private const val NODE_GPS_PRIMARY_CHANNEL_INDEX = 0

// gps_update_interval — how often the GPS chip attempts a fix. Must stay well below the
// broadcast interval, otherwise smart-broadcast (distance-triggered) can't detect movement
// between fixes and the node re-sends the same stale coordinates on the heartbeat cadence
// (field test 2026-07-09: chip left at a manually-set 120s interval looked "alive" — frequent
// packets — but the marker barely moved). Reuses the existing move-gate constant as the fix cadence.
private const val NODE_GPS_UPDATE_INTERVAL_SECS = PositionTrackingPolicy.MOBILE_MIN_GATE_SECS

// broadcast_smart_minimum_interval_secs — min gate between smart-broadcast sends while moving.
// Was never written by this preset (firmware kept whatever value it had, undocumented default) —
// mirrors the same gate already enforced for PHONE_GPS in AndroidMeshLocationManager.MOBILE_INTERVAL_MS.
private const val NODE_GPS_SMART_MIN_INTERVAL_SECS = PositionTrackingPolicy.MOBILE_MIN_GATE_SECS

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
    private val observeLocationConfig: ObserveLocationConfigUseCase,
    private val writePositionConfig: WritePositionConfigUseCase,
    private val writeChannelPositionPrecision: WriteChannelPositionPrecisionUseCase,
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
                        // combine() re-emits on every upstream tick even when the resulting pair is
                        // unchanged (e.g. our own node's position/telemetry updates re-firing
                        // observePositionSourceMode) — without this, setFixedPosition/gpsLifecycleController
                        // below would re-fire on every such tick, flooding the admin channel.
                        .distinctUntilChanged()
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
                                    ensureNodeGpsPreset(nodeNum)
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

    // Writes the NODE_GPS cadence preset only when the node's current config still diverges —
    // avoids a write (and firmware reboot) on every reconnect/tick once already configured.
    private fun ensureNodeGpsPreset(nodeNum: Int) {
        scope.launch {
            val config = observeLocationConfig(nodeNum).first()
            val isPreset = config.broadcastIntervalSecs == NODE_GPS_BROADCAST_SECS &&
                config.smartBroadcastEnabled &&
                config.smartBroadcastMinDistanceM == NODE_GPS_SMART_MIN_DIST_M &&
                config.positionFlags == NODE_GPS_POSITION_FLAGS &&
                config.primaryChannelPositionPrecision == NODE_GPS_CHANNEL_PRECISION &&
                config.gpsUpdateIntervalSecs == NODE_GPS_UPDATE_INTERVAL_SECS &&
                config.smartMinIntervalSecs == NODE_GPS_SMART_MIN_INTERVAL_SECS
            if (isPreset) return@launch

            logger.i(
                "GPS",
                "BackgroundPositionSession: writing NODE_GPS preset for node $nodeNum " +
                    "(broadcast=${NODE_GPS_BROADCAST_SECS}s, flags=$NODE_GPS_POSITION_FLAGS, precision=$NODE_GPS_CHANNEL_PRECISION, " +
                    "gpsUpdateInterval=${NODE_GPS_UPDATE_INTERVAL_SECS}s, smartMinInterval=${NODE_GPS_SMART_MIN_INTERVAL_SECS}s)",
            )
            writePositionConfig(
                destNum = nodeNum,
                gpsMode = GpsMode.ENABLED,
                broadcastSecs = NODE_GPS_BROADCAST_SECS,
                smartEnabled = true,
                smartMinDist = NODE_GPS_SMART_MIN_DIST_M,
                flags = NODE_GPS_POSITION_FLAGS,
                gpsUpdateIntervalSecs = NODE_GPS_UPDATE_INTERVAL_SECS,
                smartMinIntervalSecs = NODE_GPS_SMART_MIN_INTERVAL_SECS,
            )
            writeChannelPositionPrecision(nodeNum, NODE_GPS_PRIMARY_CHANNEL_INDEX, NODE_GPS_CHANNEL_PRECISION)
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
