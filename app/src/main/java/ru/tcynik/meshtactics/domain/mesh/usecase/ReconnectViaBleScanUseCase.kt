package ru.tcynik.meshtactics.domain.mesh.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import ru.tcynik.meshtactics.domain.logger.Logger
import ru.tcynik.meshtactics.domain.mesh.model.MeshConnectionStatus
import ru.tcynik.meshtactics.domain.mesh.repository.MeshConnectionRepository
import ru.tcynik.meshtactics.domain.usecase.base.NoParams
import ru.tcynik.meshtactics.domain.usecase.base.UseCase

/**
 * Разрывает BLE-сессию, сканирует эфир и подключается к последней ноде.
 *
 * Logcat: `tag:MT/Node syncReconnect|syncConfirm` + `tag:MT/BLE scanDevices`
 */
class ReconnectViaBleScanUseCase(
    private val disconnectFromMesh: DisconnectFromMeshUseCase,
    private val connectToDevice: ConnectToMeshDeviceUseCase,
    private val getLastConnectedDevice: GetLastConnectedDeviceUseCase,
    private val observeConnectionStatus: ObserveConnectionStatusUseCase,
    private val meshConnectionRepository: MeshConnectionRepository,
    private val logger: Logger,
) : UseCase<ReconnectViaBleScanParams, Boolean>() {

    override suspend fun invoke(params: ReconnectViaBleScanParams): Boolean {
        disconnectFromMesh(NoParams)
        delay(POST_DISCONNECT_DELAY_MS)
        step(params, "session_cleared")

        val lastDevice = getLastConnectedDevice() ?: run {
            step(params, "abort", "reason=no_last_device")
            return false
        }
        val connectParams = ConnectToMeshDeviceParams(lastDevice.address, lastDevice.name)
        step(params, "scan_target", "address=${lastDevice.address} name=${lastDevice.name}")

        repeat(params.maxAttempts) { attempt ->
            val attemptNum = attempt + 1
            step(params, "scan_start", "attempt=$attemptNum/${params.maxAttempts} timeoutMs=${params.scanWaitTimeoutMs}")

            val deviceVisible = waitForDeviceInScan(lastDevice.address, params, attemptNum)
            if (!deviceVisible) {
                step(params, "scan_miss", "attempt=$attemptNum/${params.maxAttempts}")
                if (attempt < params.maxAttempts - 1) delay(RETRY_DELAY_MS)
                return@repeat
            }

            step(params, "connect_start", "attempt=$attemptNum/${params.maxAttempts}")
            connectToDevice(connectParams)
            val connected = withTimeoutOrNull(CONNECT_ATTEMPT_TIMEOUT_MS) {
                observeConnectionStatus(NoParams)
                    .filter { it is MeshConnectionStatus.Connected }
                    .first()
            }
            if (connected != null) {
                step(params, "connected", "attempt=$attemptNum/${params.maxAttempts}")
                return true
            }

            step(
                params,
                "connect_timeout",
                "attempt=$attemptNum/${params.maxAttempts} timeoutMs=$CONNECT_ATTEMPT_TIMEOUT_MS",
            )
            disconnectFromMesh(NoParams)
            if (attempt < params.maxAttempts - 1) delay(RETRY_DELAY_MS)
        }

        disconnectFromMesh(NoParams)
        step(params, "failed", "reason=attempts_exhausted")
        return false
    }

    private suspend fun waitForDeviceInScan(
        targetAddress: String,
        params: ReconnectViaBleScanParams,
        attempt: Int,
    ): Boolean {
        val device = meshConnectionRepository.findDeviceByAddress(targetAddress, params.scanWaitTimeoutMs)
        if (device != null) {
            step(params, "scan_hit", "attempt=$attempt address=$targetAddress rssi=${device.rssi}")
        }
        return device != null
    }

    private fun step(params: ReconnectViaBleScanParams, name: String, details: String = "") {
        val callback = params.onStep
        if (callback != null) {
            callback(name, details)
        } else {
            val suffix = if (details.isEmpty()) "" else " $details"
            logger.i("Node", "${params.logTag}: $name$suffix")
        }
    }

    internal companion object {
        const val POST_DISCONNECT_DELAY_MS = 500L
        const val RETRY_DELAY_MS = 2_000L
        const val CONNECT_ATTEMPT_TIMEOUT_MS = 15_000L
        const val SCAN_WAIT_TIMEOUT_MS = 30_000L
        const val MAX_ATTEMPTS = 3
    }
}

data class ReconnectViaBleScanParams(
    val logTag: String = "syncReconnect",
    val onStep: ((name: String, details: String) -> Unit)? = null,
    val maxAttempts: Int = ReconnectViaBleScanUseCase.MAX_ATTEMPTS,
    val scanWaitTimeoutMs: Long = ReconnectViaBleScanUseCase.SCAN_WAIT_TIMEOUT_MS,
)
