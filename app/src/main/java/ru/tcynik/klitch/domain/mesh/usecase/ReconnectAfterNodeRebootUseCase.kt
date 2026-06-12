package ru.tcynik.klitch.domain.mesh.usecase



import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

import ru.tcynik.klitch.domain.channel.model.NodeSyncResult

import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository

import ru.tcynik.klitch.domain.channel.usecase.CheckNodeSyncUseCase

import ru.tcynik.klitch.domain.logger.Logger

import ru.tcynik.klitch.domain.mesh.model.MeshConnectionStatus

import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase

import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository

import ru.tcynik.klitch.domain.usecase.base.NoParams

import ru.tcynik.klitch.domain.usecase.base.UseCase



/**

 * После reboot: ждём пропадания ноды со связи, сканируем BLE в фоне,

 * подключаемся к последнему устройству при появлении в эфире.

 *

 * Logcat: `tag:MT/Node syncReconnect` — пошаговая трассировка цикла.

 */

class ReconnectAfterNodeRebootUseCase(

    private val disconnectFromMesh: DisconnectFromMeshUseCase,

    private val reconnectViaBleScan: ReconnectViaBleScanUseCase,

    private val observeConnectionStatus: ObserveConnectionStatusUseCase,

    private val requestDeviceConfig: RequestDeviceConfigUseCase,

    private val checkNodeSync: CheckNodeSyncUseCase,

    private val syncStateRepository: ContourSyncStateRepository,

    private val rebootStateRepository: RebootStateRepository,

    private val logger: Logger,

) : UseCase<NoParams, Unit>() {



    override suspend fun invoke(params: NoParams) {

        val trace = SyncReconnectTrace(logger)

        trace.step("start", "phase=${rebootStateRepository.syncCyclePhase.value}")



        delay(REBOOT_GRACE_MS)

        trace.step("grace_elapsed", "graceMs=$REBOOT_GRACE_MS")



        val disconnectResult = waitForDisconnectAfterReboot(trace)

        rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.WaitingForNode)

        trace.step("phase_waiting_for_node", disconnectResult.describe())



        val connected = reconnectViaBleScan(

            ReconnectViaBleScanParams(

                logTag = "syncReconnect",

                onStep = { name, details -> trace.step(name, details) },

            ),

        )

        if (connected) {

            trace.step("connected", "via_ble_scan")

            val syncVerified = if (rebootStateRepository.shouldSkipSyncCheckAfterReboot()) {

                verifySyncAfterReconnect(trace)

            } else {

                true

            }

            trace.step("finish", "syncVerified=$syncVerified totalMs=${trace.elapsedMs()}")

            finishRebootCycle(syncVerified = syncVerified, trace)

            return

        }



        trace.step("finish", "syncVerified=false reason=attempts_exhausted totalMs=${trace.elapsedMs()}")

        finishRebootCycle(syncVerified = false, trace)

    }



    private suspend fun waitForDisconnectAfterReboot(trace: SyncReconnectTrace): DisconnectWaitResult {

        val current = observeConnectionStatus(NoParams).first()

        trace.step("disconnect_check", "current=$current")

        if (current !is MeshConnectionStatus.Connected) {

            return DisconnectWaitResult(alreadyDisconnected = true, timedOut = false)

        }

        val disconnected = withTimeoutOrNull(DISCONNECT_WAIT_TIMEOUT_MS) {

            observeConnectionStatus(NoParams)

                .filter { it !is MeshConnectionStatus.Connected }

                .first()

        }

        return if (disconnected != null) {

            trace.step("disconnect_seen", "status=$disconnected")

            DisconnectWaitResult(alreadyDisconnected = false, timedOut = false)

        } else {

            trace.step("disconnect_timeout", "still_connected_after_ms=$DISCONNECT_WAIT_TIMEOUT_MS")

            DisconnectWaitResult(alreadyDisconnected = false, timedOut = true)

        }

    }



    private suspend fun verifySyncAfterReconnect(trace: SyncReconnectTrace): Boolean {

        requestDeviceConfig()

        trace.step("sync_verify_start", "attempts=$SYNC_VERIFY_ATTEMPTS intervalMs=$SYNC_VERIFY_INTERVAL_MS")

        repeat(SYNC_VERIFY_ATTEMPTS) { index ->

            delay(SYNC_VERIFY_INTERVAL_MS)

            val result = checkNodeSync()

            trace.step("sync_verify_check", "attempt=${index + 1}/$SYNC_VERIFY_ATTEMPTS result=$result")

            if (result is NodeSyncResult.InSync) return true

        }

        return false

    }



    private fun finishRebootCycle(syncVerified: Boolean, trace: SyncReconnectTrace) {

        if (rebootStateRepository.shouldSkipSyncCheckAfterReboot()) {

            if (syncVerified) syncStateRepository.clear()

            else syncStateRepository.setSyncRequired(true)

        }

        rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.Idle)

        rebootStateRepository.clearSkipSyncCheckAfterReboot()

        trace.step("cycle_idle", "syncVerified=$syncVerified")

    }



    private data class DisconnectWaitResult(

        val alreadyDisconnected: Boolean,

        val timedOut: Boolean,

    ) {

        fun describe(): String = when {

            alreadyDisconnected -> "already_disconnected"

            timedOut -> "timeout_still_connected"

            else -> "disconnected"

        }

    }



    private class SyncReconnectTrace(private val logger: Logger) {

        private val startedAtMs = System.currentTimeMillis()



        fun elapsedMs(): Long = System.currentTimeMillis() - startedAtMs



        fun step(name: String, details: String = "") {

            val suffix = if (details.isEmpty()) "" else " $details"

            logger.i("Node", "syncReconnect: +${elapsedMs()}ms $name$suffix")

        }

    }



    internal companion object {

        /** Минимальная пауза после reboot-команды, прежде чем ждать disconnect. */

        const val REBOOT_GRACE_MS = 2_000L

        /** @deprecated alias for tests */

        const val REBOOT_WAIT_MS = REBOOT_GRACE_MS

        const val DISCONNECT_WAIT_TIMEOUT_MS = 15_000L

        const val SYNC_VERIFY_ATTEMPTS = 5

        const val SYNC_VERIFY_INTERVAL_MS = 2_000L

    }

}


