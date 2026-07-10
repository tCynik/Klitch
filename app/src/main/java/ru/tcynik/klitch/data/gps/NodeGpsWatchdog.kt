package ru.tcynik.klitch.data.gps

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.gps.model.PositionSourceMode
import ru.tcynik.klitch.domain.gps.usecase.ObservePositionSourceModeUseCase
import ru.tcynik.klitch.domain.logger.Logger
import ru.tcynik.klitch.domain.usecase.base.NoParams
import ru.tcynik.klitch.mesh.repository.NodeRepository

/**
 * Watches a NODE_GPS node's self-reported position for a dead GPS receiver:
 * frozen position.time or zero satellites held for [STALE_THRESHOLD_MS]. On failure
 * it flips [ContourSyncStateRepository.setSyncRequired] (reusing the existing sync-confirm
 * flow rather than a separate fallback system) and emits a one-off toast event.
 */
class NodeGpsWatchdog(
    private val nodeRepository: NodeRepository,
    private val observePositionSourceMode: ObservePositionSourceModeUseCase,
    private val syncStateRepository: ContourSyncStateRepository,
    private val logger: Logger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val _staleEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val staleEvent: SharedFlow<Unit> = _staleEvent.asSharedFlow()

    private var watchJob: Job? = null

    init {
        observePositionSourceMode(NoParams)
            .onEach { mode -> onModeChanged(mode) }
            .launchIn(scope)
    }

    private fun onModeChanged(mode: PositionSourceMode) {
        watchJob?.cancel()
        watchJob = if (mode == PositionSourceMode.NODE_GPS) scope.launch { watch() } else null
    }

    private suspend fun watch() {
        var lastTime = -1
        var lastAdvanceAt = nowMs()
        var satsStaleSince = 0L
        while (true) {
            delay(CHECK_INTERVAL_MS)
            val node = nodeRepository.ourNodeInfo.value ?: continue
            val now = nowMs()
            val time = node.position.time
            val sats = node.position.sats_in_view

            if (time != lastTime) {
                lastTime = time
                lastAdvanceAt = now
            }
            val timeFrozen = now - lastAdvanceAt > STALE_THRESHOLD_MS

            satsStaleSince = if (sats <= 0) satsStaleSince.takeIf { it != 0L } ?: now else 0L
            val satsStale = satsStaleSince != 0L && now - satsStaleSince > STALE_THRESHOLD_MS

            if ((timeFrozen || satsStale) && !syncStateRepository.syncRequired.value) {
                logger.w(
                    "GPS",
                    "NodeGpsWatchdog: stale node GPS fix detected timeFrozen=$timeFrozen satsStale=$satsStale",
                )
                syncStateRepository.setSyncRequired(true)
                _staleEvent.emit(Unit)
            }
        }
    }

    private companion object {
        const val CHECK_INTERVAL_MS = 30_000L
        const val STALE_THRESHOLD_MS = 10 * 60 * 1000L
    }
}
