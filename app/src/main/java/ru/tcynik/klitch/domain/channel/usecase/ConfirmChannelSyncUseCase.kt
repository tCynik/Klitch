package ru.tcynik.klitch.domain.channel.usecase



import kotlinx.coroutines.delay

import ru.tcynik.klitch.domain.channel.model.SyncContoursResult

import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository

import ru.tcynik.klitch.domain.logger.Logger

import ru.tcynik.klitch.domain.mesh.model.NodeSyncCyclePhase

import ru.tcynik.klitch.domain.mesh.repository.RebootStateRepository

import ru.tcynik.klitch.domain.mesh.usecase.RebootNodeUseCase

import ru.tcynik.klitch.domain.mesh.usecase.ReconnectAfterNodeRebootUseCase

import ru.tcynik.klitch.domain.mesh.usecase.ReconnectViaBleScanParams

import ru.tcynik.klitch.domain.mesh.usecase.ReconnectViaBleScanUseCase

import ru.tcynik.klitch.domain.mesh.usecase.RequestDeviceConfigUseCase

import ru.tcynik.klitch.domain.usecase.base.NoParams

import ru.tcynik.klitch.domain.usecase.base.UseCase



/**

 * Полный цикл подтверждения синхронизации каналов:

 * sync → (при мёртвой сессии один быстрый BLE-reconnect) → reboot → reconnect.

 */

class ConfirmChannelSyncUseCase(

    private val syncContoursOnConnect: SyncContoursOnConnectUseCase,

    private val rebootNode: RebootNodeUseCase,

    private val reconnectAfterNodeReboot: ReconnectAfterNodeRebootUseCase,

    private val reconnectViaBleScan: ReconnectViaBleScanUseCase,

    private val requestDeviceConfig: RequestDeviceConfigUseCase,

    private val rebootStateRepository: RebootStateRepository,

    private val syncStateRepository: ContourSyncStateRepository,

    private val logger: Logger,

) : UseCase<NoParams, Unit>() {



    override suspend fun invoke(params: NoParams) {

        val phase = rebootStateRepository.syncCyclePhase.value

        if (phase == NodeSyncCyclePhase.Rebooting || phase == NodeSyncCyclePhase.WaitingForNode) {

            logger.w("Node", "syncConfirm: skipped — node cycle in phase $phase")

            return

        }

        rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.Syncing)



        when (val syncResult = runSyncWithReconnectRetry()) {

            SyncContoursResult.NothingToWrite -> {

                logger.i("Node", "syncConfirm: nothing to write")

                rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.Idle)

            }

            SyncContoursResult.Success -> {

                rebootStateRepository.markSyncAppliedBeforeReboot()

                rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.Rebooting)

                rebootNode()

                logger.i("Node", "syncConfirm: reboot_sent -> reconnect")

                syncStateRepository.clear()

                reconnectAfterNodeReboot(NoParams)

            }

            SyncContoursResult.FailedNoSession -> {

                logger.e("Node", "syncConfirm: sync failed — channels not written")

                syncStateRepository.setSyncRequired(true)

                rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.Idle)

            }

        }

    }



    private suspend fun runSyncWithReconnectRetry(): SyncContoursResult {

        when (val first = syncContoursOnConnect()) {

            SyncContoursResult.FailedNoSession -> Unit

            else -> return first

        }



        logger.i("Node", "syncConfirm: no session, quick BLE reconnect")

        rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.WaitingForNode)



        if (!reconnectViaBleScan(

                ReconnectViaBleScanParams(

                    logTag = "syncConfirm",

                    maxAttempts = 3,

                    scanWaitTimeoutMs = QUICK_SCAN_TIMEOUT_MS,

                ),

            )

        ) {

            logger.w("Node", "syncConfirm: quick BLE reconnect failed")

            return SyncContoursResult.FailedNoSession

        }



        rebootStateRepository.setSyncCyclePhase(NodeSyncCyclePhase.Syncing)

        requestDeviceConfig()

        delay(POST_CONNECT_SETTLE_MS)

        return syncContoursOnConnect()

    }



    internal companion object {

        const val POST_CONNECT_SETTLE_MS = 4_000L

        const val QUICK_SCAN_TIMEOUT_MS = 30_000L

    }

}


