package ru.tcynik.klitch.domain.mesh.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.klitch.domain.channel.model.isEmergency
import ru.tcynik.klitch.domain.channel.model.meshtasticChannelName
import ru.tcynik.klitch.domain.channel.repository.ContourRepository
import ru.tcynik.klitch.domain.channel.usecase.ObserveContoursUseCase
import ru.tcynik.klitch.domain.channel.usecase.ObserveNodeChannelsUseCase
import ru.tcynik.klitch.domain.channel.usecase.ResolveChannelSlotUseCase
import ru.tcynik.klitch.domain.channel.usecase.SlotResolution
import ru.tcynik.klitch.domain.gps.repository.GpsRepository
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.mesh.model.GpsMode
import ru.tcynik.klitch.domain.mesh.model.LocationConfigDefaults
import ru.tcynik.klitch.domain.usecase.base.NoParams
import kotlin.math.roundToInt

// Klitch position preset
private const val PRESET_BROADCAST_SECS = LocationConfigDefaults.BROADCAST_INTERVAL_SECS
private const val PRESET_POSITION_FLAGS = 897        // HEADING | SPEED | ALTITUDE | TIMESTAMP
private const val PRESET_SMART_DIST_DEFAULT_M = 20   // fallback when GPS accuracy unknown
private const val PRESET_SMART_DIST_MIN_M = 10
private const val PRESET_SMART_DIST_MAX_M = 50
private const val PRESET_SMART_DIST_ACCURACY_FACTOR = 1.5f

// Firmware factory defaults — if config matches these, node hasn't been manually configured
private const val FIRMWARE_BROADCAST_SECS = 900
private const val FIRMWARE_FLAGS = 0
private const val FIRMWARE_CHANNEL_PRECISION = 0
private const val PRESET_CHANNEL_PRECISION = 32 // full precision — app needs accurate map placement

private const val CONFIG_LOAD_TIMEOUT_MS = 10_000L
private const val CONFIG_SETTLE_DELAY_MS = 500L

class NodeProvisioningUseCase(
    private val contourRepository: ContourRepository,
    private val observeContours: ObserveContoursUseCase,
    private val writeChannel: WriteChannelUseCase,
    private val observeNodeChannels: ObserveNodeChannelsUseCase,
    private val resolveSlot: ResolveChannelSlotUseCase,
    private val observeOurNode: ObserveOurNodeUseCase,
    private val observeDeviceConfig: ObserveDeviceConfigUseCase,
    private val observeLocationConfig: ObserveLocationConfigUseCase,
    private val writePositionConfig: WritePositionConfigUseCase,
    private val setProvideLocation: SetProvideLocationUseCase,
    private val writeChannelPositionPrecision: WriteChannelPositionPrecisionUseCase,
    private val removeFixedPosition: RemoveFixedPositionUseCase,
    private val gpsRepository: GpsRepository,
    private val logger: Logger,
) {
    suspend fun provision() {
        logger.d("Node", "provision() started")
        provisionChannels()
        provisionPositionConfig()
        logger.d("Node", "provision() complete")
    }

    private suspend fun provisionChannels() {
        val contours = observeContours(NoParams).first()
        val nodeChannels = observeNodeChannels(NoParams).first()
        val primaryId = contourRepository.getPrimaryContourId()

        val usedSlots = mutableSetOf(0, 1)
        contours.forEach { contour ->
            if (contour.isEmergency) return@forEach
            if (contour.id == primaryId) return@forEach
            when (val r = resolveSlot(contour, nodeChannels, usedSlots)) {
                is SlotResolution.AlreadySynced -> {
                    logger.d("Node", "  skip '${contour.name}' — already synced at slot ${r.slot}")
                    usedSlots.add(r.slot)
                }
                is SlotResolution.FreeSlot -> {
                    logger.d("Node", "  writeChannel slot=${r.slot} name='${meshtasticChannelName(contour)}'")
                    writeChannel(r.slot, meshtasticChannelName(contour), contour.transport.meshtastic.psk)
                    usedSlots.add(r.slot)
                }
                is SlotResolution.NoFreeSlot -> logger.w("Node", "  no free slots for '${contour.name}' — skipping")
            }
        }
    }

    private suspend fun provisionPositionConfig() {
        // Wait for device config to arrive from radio before reading position settings.
        // localConfig starts as empty LocalConfig() and gets populated during initial sync;
        // without this wait, all position fields appear as firmware defaults (null → default value).
        val loaded = withTimeoutOrNull(CONFIG_LOAD_TIMEOUT_MS) {
            observeDeviceConfig(NoParams).filterNotNull().first()
        }
        if (loaded == null) {
            logger.w("Node", "  position config: device config not received in time — skip")
            return
        }
        delay(CONFIG_SETTLE_DELAY_MS)

        val nodeNum = observeOurNode(NoParams).first()?.num ?: run {
            logger.w("Node", "  position config: ourNode num unavailable — skip")
            return
        }
        val config = observeLocationConfig(nodeNum).first()

        // Stale fixed position blocks normal sharing — clear it unconditionally,
        // independent of the firmware-default guard below.
        if (config.fixedPositionEnabled) {
            logger.i("Node", "  position config: removing stale fixed position")
            removeFixedPosition(nodeNum)
        }

        // Write preset only when all position settings still match firmware defaults.
        // After first provisioning, broadcastIntervalSecs becomes 1800 (≠ 900) and
        // smartBroadcastEnabled becomes true — so this check will be false on subsequent
        // connects, preventing re-write and avoiding unnecessary node reboots.
        val isFirmwareDefault = config.gpsMode == GpsMode.DISABLED
            && config.broadcastIntervalSecs == FIRMWARE_BROADCAST_SECS
            && !config.smartBroadcastEnabled
            && config.smartBroadcastMinDistanceM == 0
            && config.positionFlags == FIRMWARE_FLAGS
            && !config.provideLocationToMesh
            && config.primaryChannelPositionPrecision == FIRMWARE_CHANNEL_PRECISION

        if (!isFirmwareDefault) {
            logger.d("Node", "  position config: already configured (broadcast=${config.broadcastIntervalSecs}s, smart=${config.smartBroadcastEnabled}) — skip")
            return
        }

        val smartMinDist = computeSmartMinDist()
        logger.i("Node", "  position config: writing Klitch preset " +
            "(broadcast=${PRESET_BROADCAST_SECS}s, smart=true, minDist=${smartMinDist}m, flags=${PRESET_POSITION_FLAGS}, " +
            "provideLocation=true, channelPrecision=${PRESET_CHANNEL_PRECISION})")
        writePositionConfig(
            destNum = nodeNum,
            gpsMode = GpsMode.DISABLED,
            broadcastSecs = PRESET_BROADCAST_SECS,
            smartEnabled = true,
            smartMinDist = smartMinDist,
            flags = PRESET_POSITION_FLAGS,
        )
        setProvideLocation(nodeNum, true)
        writeChannelPositionPrecision(nodeNum, 0, PRESET_CHANNEL_PRECISION)
    }

    // GPS accuracy → smart broadcast min distance.
    // clamp(accuracy × 1.5, 10m, 50m): finer threshold on good signal, coarser on poor signal.
    private fun computeSmartMinDist(): Int {
        val accuracy = gpsRepository.location.value?.accuracy ?: return PRESET_SMART_DIST_DEFAULT_M
        return (accuracy * PRESET_SMART_DIST_ACCURACY_FACTOR)
            .roundToInt()
            .coerceIn(PRESET_SMART_DIST_MIN_M, PRESET_SMART_DIST_MAX_M)
    }
}
